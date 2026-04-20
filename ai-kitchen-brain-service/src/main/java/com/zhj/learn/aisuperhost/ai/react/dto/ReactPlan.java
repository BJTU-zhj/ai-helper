package com.zhj.learn.aisuperhost.ai.react.dto;

import com.zhj.learn.aisuperhost.ai.react.enums.ReactActionType;
import org.springframework.util.StringUtils;

/**
 * ReAct 单步规划协议模型（强约束版本）。
 *
 * <p>这是 Planner 模型每一步必须输出的统一结构。后续 Agent 编排层只消费本对象，
 * 不直接依赖大模型的自然语言文本，从而降低解析歧义并便于审计。
 *
 * <p>字段语义：
 * 1. actionType: 本步动作类型（ANSWER / TOOL_CALL），必填。
 * 2. thoughtSummary: 思考摘要（简短），用于日志与调试，不向最终用户原样暴露。
 * 3. toolCall: 当 actionType=TOOL_CALL 时必填；当 actionType=ANSWER 时必须为空。
 * 4. finalAnswer: 当 actionType=ANSWER 时必填；当 actionType=TOOL_CALL 时必须为空。
 *
 * <p>使用方式：
 * 1. 反序列化后必须调用 {@link #validateStrongConstraints()}。
 * 2. 校验失败直接抛错，让上层走降级策略（例如直答或重试）。
 */
public class ReactPlan {

    private ReactActionType actionType;

    private String thoughtSummary;

    private ReactToolCall toolCall;

    private String finalAnswer;

    public ReactActionType getActionType() {
        return actionType;
    }

    public void setActionType(ReactActionType actionType) {
        this.actionType = actionType;
    }

    public String getThoughtSummary() {
        return thoughtSummary;
    }

    public void setThoughtSummary(String thoughtSummary) {
        this.thoughtSummary = thoughtSummary;
    }

    public ReactToolCall getToolCall() {
        return toolCall;
    }

    public void setToolCall(ReactToolCall toolCall) {
        this.toolCall = toolCall;
    }

    public String getFinalAnswer() {
        return finalAnswer;
    }

    public void setFinalAnswer(String finalAnswer) {
        this.finalAnswer = finalAnswer;
    }

    /**
     * 强约束校验：保证协议字段与动作类型严格一致。
     *
     * <p>规则：
     * 1. actionType 必填。
     * 2. TOOL_CALL:
     *    - toolCall 必填且 toolName 必填。
     *    - finalAnswer 必须为空。
     * 3. ANSWER:
     *    - finalAnswer 必填。
     *    - toolCall 必须为空。
     *
     * @throws IllegalArgumentException 校验不通过时抛出。
     */
    public void validateStrongConstraints() {
        if (actionType == null) {
            throw new IllegalArgumentException("ReactPlan.actionType must not be null");
        }
        if (actionType == ReactActionType.TOOL_CALL) {
            validateToolCallPlan();
            return;
        }
        validateAnswerPlan();
    }

    private void validateToolCallPlan() {
        if (toolCall == null) {
            throw new IllegalArgumentException("ReactPlan.toolCall must not be null when actionType=TOOL_CALL");
        }
        if (!StringUtils.hasText(toolCall.getToolName())) {
            throw new IllegalArgumentException("ReactPlan.toolCall.toolName must not be blank when actionType=TOOL_CALL");
        }
        if (StringUtils.hasText(finalAnswer)) {
            throw new IllegalArgumentException("ReactPlan.finalAnswer must be empty when actionType=TOOL_CALL");
        }
    }

    private void validateAnswerPlan() {
        if (!StringUtils.hasText(finalAnswer)) {
            throw new IllegalArgumentException("ReactPlan.finalAnswer must not be blank when actionType=ANSWER");
        }
        if (toolCall != null) {
            throw new IllegalArgumentException("ReactPlan.toolCall must be null when actionType=ANSWER");
        }
    }
}
