package com.zhj.learn.aisuperhost.ai.react.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhj.learn.aisuperhost.ai.react.dotools.ReactToolExecutor;
import com.zhj.learn.aisuperhost.ai.react.dto.ReactPlan;
import com.zhj.learn.aisuperhost.ai.react.dto.ReactStep;
import com.zhj.learn.aisuperhost.ai.react.dto.ReactStreamEvent;
import com.zhj.learn.aisuperhost.ai.react.enums.ReactActionType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * ReAct 多步 Agent 主循环服务。
 *
 * <p>职责：
 * 1. 维护 step 历史并驱动 Planner 做单步决策；
 * 2. 在 TOOL_CALL 分支执行工具，并把 observation 回灌到下一步；
 * 3. 在 ANSWER 分支直接返回结果；
 * 4. 发生异常或达到步数上限时执行降级策略，保证请求可返回。
 *
 * <p>说明：
 * 当前版本以“可运行+可观测”为优先，适合学习和迭代。
 */
@Service
@Slf4j
public class ReactAgentService {

    // 步骤状态
    private static final String STEP_STATUS_PLANNED = "PLANNED";
    private static final String STEP_STATUS_EXECUTED = "EXECUTED";
    private static final String STEP_STATUS_FAILED = "FAILED";

    private final ReactPlannerService reactPlannerService;
    private final ReactToolExecutor reactToolExecutor;
    private final ChatClient qwenChatClient;
    private final ChatClient qwenPlannerChatClient;
    private final ObjectMapper objectMapper;

    /**
     * 最大步骤数，超过后触发兜底回答。
     */
    private final int maxSteps;

    /**
     * 历史摘要最大字符数，控制 planner 输入体积。
     */
    private final int historySummaryMaxLength;

    public ReactAgentService(ReactPlannerService reactPlannerService,
                             ReactToolExecutor reactToolExecutor,
                             @Qualifier("qwenChatClient") ChatClient qwenChatClient,
                             @Qualifier("qwenPlannerChatClient") ChatClient qwenPlannerChatClient,
                             ObjectMapper objectMapper,
                             @Value("${app.react.max-steps:10}") int maxSteps,
                             @Value("${app.react.history-summary-max-length:1800}") int historySummaryMaxLength) {
        this.reactPlannerService = reactPlannerService;
        this.reactToolExecutor = reactToolExecutor;
        this.qwenChatClient = qwenChatClient;
        this.qwenPlannerChatClient = qwenPlannerChatClient;
        this.objectMapper = objectMapper;
        this.maxSteps = Math.max(1, maxSteps);
        this.historySummaryMaxLength = Math.max(400, historySummaryMaxLength);
    }

    /**
     * 执行多步 ReAct。
     *
     * @param memoryId 会话 ID（用于与现有记忆体系兼容）
     * @param userInput 用户输入
     * @return 最终回复
     */
    public String run(String memoryId, String userInput) {
        return run(memoryId, userInput, event -> {
        });
    }

    /**
     * 执行多步 ReAct，并通过事件回调暴露执行过程。
     *
     * @param memoryId 会话 ID
     * @param userInput 用户输入
     * @param eventConsumer ReAct 过程事件消费者，可用于 SSE 推送
     * @return 最终回复
     */
    public String run(String memoryId, String userInput, Consumer<ReactStreamEvent> eventConsumer) {
        if (!StringUtils.hasText(userInput)) {
            throw new IllegalArgumentException("userInput must not be blank");
        }

        //存放处理步骤记忆
        List<ReactStep> steps = new ArrayList<>();
        //获取可用工具列表
        List<String> availableTools = reactToolExecutor.listAvailableToolDefinitions();
        emit(eventConsumer, ReactStreamEvent.of("start", "ReAct started"));
        log.info("ReAct started. memoryId={}, maxSteps={}, availableTools={}", memoryId, maxSteps, availableTools);

        for (int stepNo = 1; stepNo <= maxSteps; stepNo++) {
            //创建本轮历史对象并且初始化
            ReactStep step = new ReactStep();
            step.setStepNo(stepNo);
            step.setStartedAt(LocalDateTime.now());
            step.setStatus(STEP_STATUS_PLANNED);

            try {
                //构建历史摘要
                String stepHistorySummary = buildStepHistorySummary(steps);
                //思考
                ReactPlan plan = reactPlannerService.planNextStep(memoryId, userInput, stepHistorySummary, availableTools);
                //获取下一步的动作类型和思考摘要
                step.setActionType(plan.getActionType());
                step.setThoughtSummary(plan.getThoughtSummary());
                emit(eventConsumer, ReactStreamEvent.fromStep("step_planned", step));

                //判断是否为ANSWER，是的话返回结果
                if (plan.getActionType() == ReactActionType.ANSWER) {
                    step.setFinalAnswer(plan.getFinalAnswer());
                    step.setStatus(STEP_STATUS_EXECUTED);
                    step.setFinishedAt(LocalDateTime.now());
                    steps.add(step);
                    emit(eventConsumer, ReactStreamEvent.fromStep("final_answer", step));
                    emit(eventConsumer, ReactStreamEvent.of("done", "ReAct finished"));

                    log.info("ReAct finished with ANSWER. memoryId={}, stepNo={}", memoryId, stepNo);
                    return plan.getFinalAnswer();
                }

                //不是ANSWER，则执行工具
                Map<String, Object> toolArgs = plan.getToolCall() == null ? Map.of() : plan.getToolCall().getToolArgs();
                String toolName = plan.getToolCall() == null ? "" : plan.getToolCall().getToolName();
                step.setToolName(toolName);
                step.setToolArgs(toolArgs);
                emit(eventConsumer, ReactStreamEvent.fromStep("tool_started", step));

                String observation = reactToolExecutor.executeTool(toolName, toolArgs);
                step.setObservation(observation);
                step.setStatus(STEP_STATUS_EXECUTED);
                step.setFinishedAt(LocalDateTime.now());
                steps.add(step);
                emit(eventConsumer, ReactStreamEvent.fromStep("tool_finished", step));

                log.info("ReAct tool executed. memoryId={}, stepNo={}, toolName={}", memoryId, stepNo, toolName);
            } catch (Exception e) {
                step.setStatus(STEP_STATUS_FAILED);
                step.setErrorMessage(e.getMessage());
                step.setFinishedAt(LocalDateTime.now());
                steps.add(step);
                emit(eventConsumer, ReactStreamEvent.fromStep("step_failed", step));
                //某一步失败，触发降级
                log.error("ReAct step failed. memoryId={}, stepNo={}", memoryId, stepNo, e);
                return degradeToDirectAnswer(memoryId, userInput, steps, "step-exception", eventConsumer);
            }
        }

        log.warn("ReAct reached max steps. memoryId={}, maxSteps={}", memoryId, maxSteps);
        return degradeToDirectAnswer(memoryId, userInput, steps, "max-steps-reached", eventConsumer);
    }

    /**
     * 降级为直接回答
     *
     * @param memoryId 会话 ID
     * @param userInput 用户输入
     * @param steps 步骤列表
     * @param reason 降级原因
     * @return 降级后的回答
     */
    private String degradeToDirectAnswer(String memoryId,
                                         String userInput,
                                         List<ReactStep> steps,
                                         String reason,
                                         Consumer<ReactStreamEvent> eventConsumer) {
        ReactStreamEvent degradeEvent = ReactStreamEvent.of("degrade_started", "ReAct fallback started");
        degradeEvent.setReason(reason);
        emit(eventConsumer, degradeEvent);

        String stepSummary = buildStepHistorySummary(steps);
        String prompt = "当前多步工具流程未正常收敛，原因：" + reason + "。\n"
                + "请基于用户问题与已有步骤摘要给出一个尽可能有帮助的回答。\n"
                + "步骤摘要：\n" + stepSummary + "\n";

        String answer = qwenPlannerChatClient.prompt()
                .system(prompt)
                .user(userInput)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, memoryId)
                        .param("raw_user_input", userInput))
                .call()
                .content();
        ReactStreamEvent finalEvent = ReactStreamEvent.of("final_answer", "fallback answer");
        finalEvent.setFinalAnswer(answer);
        emit(eventConsumer, finalEvent);
        emit(eventConsumer, ReactStreamEvent.of("done", "ReAct fallback finished"));
        return answer;
    }

    /**
     * 构建步骤摘要
     *
     * 示例：
     * step=1, status=EXECUTED, action=TOOL_CALL, thought=用户想做红烧肉且怕胖，我需要先去知识库查一下红烧肉的做法和低脂平替方案, tool=query_knowledge_base, args={"keyword":"红烧肉 减脂 平替"}, observation=structuredContent={"title":"红烧肉平替方案","content":"建议使用鹌鹑蛋和瘦肉，或者在烹饪前先将五花肉煸炒出多余油脂。"}
     * step=2, status=FAILED, action=TOOL_CALL, thought=我想查一下用户冰箱里现在有没有鹌鹑蛋, tool=check_fridge_inventory, args={"item":"鹌鹑蛋"}, error=Tool not found or execution failed: check_fridge_inventory. Available tools: query_knowledge_base, weather_search
     * step=3, status=EXECUTED, action=ANSWER, thought=虽然查不到冰箱库存，但我可以把减脂版的做法直接告诉用户, finalAnswer=如果你怕胖，建议你在做红烧肉时先用平底锅把五花肉多余的油脂煎出来，或者加入一些百叶结吸收油脂哦！
     * @param steps
     * @return
     */
    private String buildStepHistorySummary(List<ReactStep> steps) {
        if (steps == null || steps.isEmpty()) {
            return "无";
        }

        String summary = steps.stream()
                .map(this::toStepSummaryLine)
                .collect(Collectors.joining("\n"));

        if (summary.length() <= historySummaryMaxLength) {
            return summary;
        }
        return summary.substring(0, historySummaryMaxLength) + "...(truncated)";
    }

    /**
     * 转换为 JSON 字符串，如果转换失败则返回原始对象。
     *
     * @param value
     * @return
     */
    private String toStepSummaryLine(ReactStep step) {
        StringBuilder sb = new StringBuilder();
        sb.append("step=").append(step.getStepNo())
                .append(", status=").append(step.getStatus())
                .append(", action=").append(step.getActionType());

        if (StringUtils.hasText(step.getThoughtSummary())) {
            sb.append(", thought=").append(step.getThoughtSummary());
        }
        if (StringUtils.hasText(step.getToolName())) {
            sb.append(", tool=").append(step.getToolName());
        }
        if (step.getToolArgs() != null && !step.getToolArgs().isEmpty()) {
            sb.append(", args=").append(toJsonSafely(step.getToolArgs()));
        }
        if (StringUtils.hasText(step.getObservation())) {
            sb.append(", observation=").append(step.getObservation());
        }
        if (StringUtils.hasText(step.getErrorMessage())) {
            sb.append(", error=").append(step.getErrorMessage());
        }
        if (StringUtils.hasText(step.getFinalAnswer())) {
            sb.append(", finalAnswer=").append(step.getFinalAnswer());
        }
        return sb.toString();
    }

    private String toJsonSafely(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    private void emit(Consumer<ReactStreamEvent> consumer, ReactStreamEvent event) {
        if (consumer == null || event == null) {
            return;
        }
        consumer.accept(event);
    }
}
