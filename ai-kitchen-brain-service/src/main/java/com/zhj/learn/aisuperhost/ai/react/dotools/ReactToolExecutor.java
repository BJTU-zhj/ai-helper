package com.zhj.learn.aisuperhost.ai.react.dotools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpSyncClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ReAct 工具执行器（统一入口）。
 *
 * <p>设计目标：
 * 1. 统一调用本地 Tool 与远程 MCP Tool，避免在 Agent 主循环中写分支细节。
 * 2. 返回标准化 observation 字符串，供下一步 Planner 继续推理。
 * 3. 仅做“执行”，不做规划；工具名和参数来自已校验的 ReactPlan。
 *
 * <p>实现策略：
 * - 统一从 Spring 容器中的 {@link ToolCallbackProvider} 动态发现可用工具。
 * - 本地 @Tool 与远程 MCP tool 都映射为 ToolCallback，执行路径保持一致。
 */
@Service
@Slf4j
public class ReactToolExecutor {

    private final ObjectProvider<ToolCallbackProvider> toolCallbackProvider;

    private final ObjectProvider<List<McpSyncClient>> mcpSyncClientListProvider;

    private final ObjectProvider<McpSyncClient> mcpSyncClientProvider;

    private final ObjectMapper objectMapper;

    /**
     * observation 长度上限，防止单次工具结果过长导致后续 token 暴涨。
     */
    private final int observationMaxLength;

    public ReactToolExecutor(ObjectProvider<ToolCallbackProvider> toolCallbackProvider,
                             ObjectProvider<List<McpSyncClient>> mcpSyncClientListProvider,
                             ObjectProvider<McpSyncClient> mcpSyncClientProvider,
                             ObjectMapper objectMapper,
                             @Value("${app.react.observation-max-length:1500}") int observationMaxLength) {
        this.toolCallbackProvider = toolCallbackProvider;
        this.mcpSyncClientListProvider = mcpSyncClientListProvider;
        this.mcpSyncClientProvider = mcpSyncClientProvider;
        this.objectMapper = objectMapper;
        this.observationMaxLength = Math.max(200, observationMaxLength);
    }

    /**
     * 执行指定工具。
     *
     * @param toolName Planner 规划得到的工具名
     * @param toolArgs Planner 规划得到的参数
     * @return 规范化 observation 文本
     */
    public String executeTool(String toolName, Map<String, Object> toolArgs) {
        if (!StringUtils.hasText(toolName)) {
            throw new IllegalArgumentException("toolName must not be blank");
        }

        Map<String, Object> safeArgs = toolArgs == null ? Map.of() : toolArgs;
        List<ToolCallback> callbacks = listAllToolCallbacks();
        String requested = toolName.trim();
        String argsJson = toJsonSafely(safeArgs);
        Exception lastExecutionException = null;
        String matchedToolName = null;

        for (ToolCallback callback : callbacks) {
            if (callback == null || callback.getToolDefinition() == null) {
                continue;
            }
            String callbackToolName = callback.getToolDefinition().name();
            if (!StringUtils.hasText(callbackToolName)) {
                continue;
            }
            if (!callbackToolName.trim().equalsIgnoreCase(requested)) {
                continue;
            }
            matchedToolName = callbackToolName.trim();
            try {
                String callbackResult = callback.call(argsJson);
                return trimObservation(StringUtils.hasText(callbackResult)
                        ? callbackResult
                        : "Tool callback executed with empty result");
            } catch (Exception e) {
                lastExecutionException = e;
                log.warn("Tool callback execution failed. requestedTool={}, callbackTool={}",
                        requested, callbackToolName, e);
            }
        }

        if (lastExecutionException != null) {
            throw new IllegalArgumentException("Tool execution failed: " + requested
                    + ". Args: " + argsJson
                    + ". Error: " + rootCauseMessage(lastExecutionException), lastExecutionException);
        }

        String available = listAvailableToolNames().stream().collect(Collectors.joining(", "));
        throw new IllegalArgumentException("Tool not found: " + requested
                + (matchedToolName == null ? "" : ". Matched tool: " + matchedToolName)
                + ". Available tools: " + available);
    }

    /**
     * 返回当前 Agent 可见的工具名列表，供 Planner 模板白名单注入使用。
     */
    public List<String> listAvailableToolNames() {
        Set<String> names = new LinkedHashSet<>();

        for (ToolCallback callback : listAllToolCallbacks()) {
            if (callback == null || callback.getToolDefinition() == null) {
                continue;
            }
            String name = callback.getToolDefinition().name();
            if (StringUtils.hasText(name)) {
                names.add(name);
            }
        }

        return new ArrayList<>(names);
    }

    /**
     * 返回当前 Agent 可见的工具定义，供 Planner 理解工具用途与参数结构。
     */
    public List<String> listAvailableToolDefinitions() {
        Set<String> definitions = new LinkedHashSet<>();

        for (ToolCallback callback : listAllToolCallbacks()) {
            if (callback == null || callback.getToolDefinition() == null) {
                continue;
            }
            String name = callback.getToolDefinition().name();
            if (!StringUtils.hasText(name)) {
                continue;
            }

            String description = callback.getToolDefinition().description();
            String inputSchema = callback.getToolDefinition().inputSchema();
            definitions.add("- name: " + name.trim()
                    + "\n  description: " + (StringUtils.hasText(description) ? description.trim() : "无")
                    + "\n  inputSchema: " + (StringUtils.hasText(inputSchema) ? inputSchema.trim() : "{}"));
        }

        return new ArrayList<>(definitions);
    }

    /**
     * 递归收集所有 ToolCallbackProvider 中的工具。
     */

    private List<ToolCallback> listAllToolCallbacks() {
        Map<String, ToolCallback> callbacks = new java.util.LinkedHashMap<>();

        for (ToolCallbackProvider provider : toolCallbackProvider.orderedStream().toList()) {
            if (provider == null) {
                continue;
            }
            collectToolCallbacks(callbacks, provider, provider.getClass().getName());
        }

        List<McpSyncClient> mcpClients = listAllMcpSyncClients();
        if (!mcpClients.isEmpty()) {
            collectToolCallbacks(callbacks, new SyncMcpToolCallbackProvider(mcpClients), "directMcpToolCallbackProvider");
        }

        return new ArrayList<>(callbacks.values());
    }

    /**
     * 递归收集所有 MCP SyncClient 中的工具。
     */
    private List<McpSyncClient> listAllMcpSyncClients() {
        Set<McpSyncClient> clients = new LinkedHashSet<>();
        mcpSyncClientListProvider.orderedStream()
                .filter(list -> list != null && !list.isEmpty())
                .forEach(clients::addAll);
        clients.addAll(mcpSyncClientProvider.orderedStream().toList());
        return new ArrayList<>(clients);
    }

    /**
     * 递归收集某个 ToolCallbackProvider 中的所有工具。
     */
    private void collectToolCallbacks(Map<String, ToolCallback> callbacks,
                                      ToolCallbackProvider provider,
                                      String providerName) {
        try {
            ToolCallback[] providerCallbacks = provider.getToolCallbacks();
            if (providerCallbacks == null) {
                return;
            }
            for (ToolCallback callback : providerCallbacks) {
                if (callback == null || callback.getToolDefinition() == null) {
                    continue;
                }
                String name = callback.getToolDefinition().name();
                if (StringUtils.hasText(name)) {
                    callbacks.putIfAbsent(name, callback);
                }
            }
        } catch (Exception e) {
            log.warn("list tool callbacks failed. provider={}", providerName, e);
        }
    }

    private String toJsonSafely(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (StringUtils.hasText(message)) {
            return message.trim();
        }
        return current.getClass().getSimpleName();
    }


    /**
     * 截断 Observation，避免长度超出限制。
     */
    private String trimObservation(String observation) {
        if (!StringUtils.hasText(observation)) {
            return "";
        }
        String text = observation.trim();
        if (text.length() <= observationMaxLength) {
            return text;
        }
        return text.substring(0, observationMaxLength) + "...(truncated)";
    }

}
