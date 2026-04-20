package com.zhj.learn.aisuperhost.ai.react.dotools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhj.learn.aisuperhost.ai.tools.PlanDocxTools;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
 * <p>当前支持：
 * - 本地 Tool：generatePlanDocx（PlanDocxTools）
 * - 远程 MCP Tool：遍历所有已发现 McpSyncClient 的工具并按名称匹配执行
 */
@Service
@Slf4j
public class ReactToolExecutor {

    private final ObjectProvider<McpSyncClient> mcpSyncClientProvider;

    private final PlanDocxTools planDocxTools;

    private final ObjectMapper objectMapper;

    /**
     * observation 长度上限，防止单次工具结果过长导致后续 token 暴涨。
     */
    private final int observationMaxLength;

    public ReactToolExecutor(ObjectProvider<McpSyncClient> mcpSyncClientProvider,
                             PlanDocxTools planDocxTools,
                             ObjectMapper objectMapper,
                             @Value("${app.react.observation-max-length:1500}") int observationMaxLength) {
        this.mcpSyncClientProvider = mcpSyncClientProvider;
        this.planDocxTools = planDocxTools;
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

        if ("generatePlanDocx".equalsIgnoreCase(toolName)) {
            return executeLocalDocxTool(safeArgs);
        }

        return executeMcpTool(toolName, safeArgs);
    }

    /**
     * 返回当前 Agent 可见的工具名列表，供 Planner 模板白名单注入使用。
     */
    public List<String> listAvailableToolNames() {
        Set<String> names = new LinkedHashSet<>();
        names.add("generatePlanDocx");

        for (McpSyncClient client : mcpSyncClientProvider.orderedStream().toList()) {
            try {
                McpSchema.ListToolsResult result = client.listTools();
                if (result == null || result.tools() == null) {
                    continue;
                }
                result.tools().forEach(tool -> {
                    if (tool != null && StringUtils.hasText(tool.name())) {
                        names.add(tool.name());
                    }
                });
            } catch (Exception e) {
                log.warn("list MCP tools failed. server={}",
                        client.getServerInfo() == null ? "unknown" : client.getServerInfo().name(), e);
            }
        }

        return new ArrayList<>(names);
    }


    /**
     * 执行本地工具：generatePlanDocx
     */
    private String executeLocalDocxTool(Map<String, Object> toolArgs) {
        String title = asString(toolArgs.get("title"));
        String planContent = asString(toolArgs.get("planContent"));
        String path = planDocxTools.generatePlanDocx(title, planContent);
        return trimObservation("DOCX generated: " + path);
    }


    /**
     * 执行远程 MCP 工具
     */
    private String executeMcpTool(String toolName, Map<String, Object> toolArgs) {
        List<McpSyncClient> clients = mcpSyncClientProvider.orderedStream().toList();
        if (clients.isEmpty()) {
            throw new IllegalStateException("No MCP clients available");
        }

        for (McpSyncClient client : clients) {
            try {
                // 获取工具列表
                McpSchema.ListToolsResult listToolsResult = client.listTools();
                if (listToolsResult == null || listToolsResult.tools() == null) {
                    continue;
                }
                // 匹配工具名
                String matchedToolName = findMatchedToolName(listToolsResult.tools(), toolName);
                if (!StringUtils.hasText(matchedToolName)) {
                    continue;
                }
                // 调用工具
                McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(matchedToolName, toolArgs);
                McpSchema.CallToolResult result = client.callTool(request);
                return trimObservation(formatMcpCallResult(result));
            } catch (Exception e) {
                log.warn("MCP tool execution failed. requestedTool={}, server={}",
                        toolName,
                        client.getServerInfo() == null ? "unknown" : client.getServerInfo().name(),
                        e);
            }
        }
        //获取当前可以获取的工具列表
        String available = listAvailableToolNames().stream().collect(Collectors.joining(", "));
        throw new IllegalArgumentException("Tool not found or execution failed: " + toolName
                + ". Available tools: " + available);
    }

    /**
     * 尝试找到匹配的tool名字并返回。
     *
     * @param obj 工具结果对象
     * @return JSON 字符串
     */
    private String findMatchedToolName(List<McpSchema.Tool> tools, String requestedToolName) {
        String requested = requestedToolName.trim().toLowerCase(Locale.ROOT);
        for (McpSchema.Tool tool : tools) {
            if (tool == null || !StringUtils.hasText(tool.name())) {
                continue;
            }
            if (tool.name().trim().toLowerCase(Locale.ROOT).equals(requested)) {
                return tool.name();
            }
        }
        return null;
    }

    /**
     * 格式化 MCP 工具调用结果。
     *
     * @param result MCP 工具调用结果
     * @return 格式化后的结果
     */
    private String formatMcpCallResult(McpSchema.CallToolResult result) {
        if (result == null) {
            return "MCP result is null";
        }

        StringBuilder sb = new StringBuilder();
        if (Boolean.TRUE.equals(result.isError())) {
            sb.append("MCP tool returned error. ");
        }

        if (result.structuredContent() != null) {
            sb.append("structuredContent=").append(toJsonSafely(result.structuredContent())).append(' ');
        }

        if (result.content() != null && !result.content().isEmpty()) {
            String contentText = result.content().stream()
                    .map(content -> {
                        if (content instanceof McpSchema.TextContent textContent) {
                            return textContent.text();
                        }
                        return String.valueOf(content);
                    })
                    .filter(StringUtils::hasText)
                    .collect(Collectors.joining("\n"));
            if (StringUtils.hasText(contentText)) {
                sb.append("content=").append(contentText);
            }
        }

        String formatted = sb.toString().trim();
        return StringUtils.hasText(formatted) ? formatted : String.valueOf(result);
    }

    private String toJsonSafely(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
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

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
