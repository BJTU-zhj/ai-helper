package com.zhj.learn.aisuperhost.ai.react.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhj.learn.aisuperhost.ai.react.dto.ReactPlan;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ReAct Planner 服务：
 * 负责把“用户问题 + 步骤历史摘要 + 可用工具清单”转换为单步结构化规划（ReactPlan）。
 *
 * <p>职责边界：
 * 1. 只负责“规划”而不负责“执行”工具。
 * 2. 输出必须是强约束 JSON，并且通过 {@link ReactPlan#validateStrongConstraints()} 校验。
 * 3. 若模型返回非 JSON 或字段不合法，直接抛错，由上层 Agent 服务决定重试或降级。
 */
@Service
@Slf4j
public class ReactPlannerService {

    @Resource
    @Qualifier("qwenChatClient")
    private ChatClient qwenChatClient;

    @Resource
    private ObjectMapper objectMapper;

    @Value("classpath:template/react-planner.st")
    private org.springframework.core.io.Resource plannerTemplateResource;

    private String plannerTemplate;

    /**
     * 启动时加载 planner 模板，避免每次请求重复 IO。
     */
    @PostConstruct
    public void initTemplate() throws IOException {
        this.plannerTemplate = StreamUtils.copyToString(
                plannerTemplateResource.getInputStream(),
                StandardCharsets.UTF_8
        );
    }

    /**
     * 规划下一步动作。
     *
     * @param userInput 当前轮用户输入
     * @param stepHistorySummary 历史步骤摘要（本轮临时状态，不是长期记忆）
     * @param availableTools 允许调用的工具名白名单
     * @return 结构化单步计划
     */
    public ReactPlan planNextStep(String userInput, String stepHistorySummary, List<String> availableTools) {
        if (!StringUtils.hasText(userInput)) {
            throw new IllegalArgumentException("userInput must not be blank");
        }
        if (availableTools == null || availableTools.isEmpty()) {
            throw new IllegalArgumentException("availableTools must not be empty");
        }
        // 渲染 Prompt
        String renderedPrompt = renderPlannerPrompt(userInput, stepHistorySummary, availableTools);
        String modelOutput = qwenChatClient.prompt()
                .system(renderedPrompt)
                .user("请严格按系统要求输出JSON对象。")
                .call()
                .content();

        ReactPlan plan = parseAndValidatePlan(modelOutput);
        log.debug("ReAct planner output: actionType={}, thoughtSummary={}",
                plan.getActionType(), plan.getThoughtSummary());
        return plan;
    }

    /**
     * 渲染 planner 模板。
     *
     * @param userInput 当前轮用户输入
     * @param stepHistorySummary 历史步骤摘要（本轮临时状态，不是长期记忆）
     * @param availableTools 允许调用的工具名白名单
     * @return 渲染后的 Prompt
     */

    private String renderPlannerPrompt(String userInput, String stepHistorySummary, List<String> availableTools) {
        String toolsText = availableTools.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .sorted()
                .map(name -> "- " + name)
                .collect(Collectors.joining("\n"));

        String historyText = StringUtils.hasText(stepHistorySummary)
                ? stepHistorySummary.trim()
                : "无";

        return plannerTemplate
                .replace("<available_tools>", toolsText)
                .replace("<user_input>", userInput.trim())
                .replace("<step_history_summary>", historyText);
    }

    /**
     * 解析模型输出并校验。
     *
     * @param modelOutput 模型输出
     * @return 模型输出的强约束 JSON 对象
     */
    private ReactPlan parseAndValidatePlan(String modelOutput) {
        if (!StringUtils.hasText(modelOutput)) {
            throw new IllegalStateException("Planner output is empty");
        }

        // 尝试提取 JSON
        String json = extractJsonObject(modelOutput);
        try {
            ReactPlan plan = objectMapper.readValue(json, ReactPlan.class);
            plan.validateStrongConstraints();
            return plan;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Planner output is not valid JSON: " + json, e);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Planner output violates ReactPlan constraints: " + json, e);
        }
    }

    /**
     * 兼容模型常见输出形态：
     * 1. 纯 JSON 对象
     * 2. ```json ... ``` 代码块
     * 3. JSON 前后混入解释文本（尽可能提取第一个 JSON 对象）
     */
    private String extractJsonObject(String raw) {
        String text = raw.trim();

        if (text.startsWith("```")) {
            int firstLineEnd = text.indexOf('\n');
            int fenceEnd = text.lastIndexOf("```");
            if (firstLineEnd > 0 && fenceEnd > firstLineEnd) {
                text = text.substring(firstLineEnd + 1, fenceEnd).trim();
            }
        }

        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }
}
