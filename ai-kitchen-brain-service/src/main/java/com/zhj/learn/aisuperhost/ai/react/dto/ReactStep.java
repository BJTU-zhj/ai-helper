package com.zhj.learn.aisuperhost.ai.react.dto;

import com.zhj.learn.aisuperhost.ai.react.enums.ReactActionType;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ReAct 单步执行记录----记录了一轮思考，行动的全部信息，可以最为记忆传给大模型。
 *
 * <p>用途：
 * 1. 在本轮内维护 thought -> action -> observation 的执行轨迹。
 * 2. 为下一步 Planner 提供历史摘要输入。
 * 3. 为后续审计日志留出结构化数据字段（不直接写入长期会话记忆）。
 *
 * <p>建议：
 * - 该对象应作为“本轮临时状态”使用，轮次结束后可只保留必要摘要。
 */
public class ReactStep {

    /**
     * 步骤序号，从 1 开始递增。
     */
    private int stepNo;

    /**
     * 本步动作类型（ANSWER / TOOL_CALL）。
     */
    private ReactActionType actionType;

    /**
     * 大模型给出的简短思考摘要（用于日志/调试）。
     */
    private String thoughtSummary;

    /**
     * 工具名称。
     * 当 actionType=TOOL_CALL 时通常有值；ANSWER 时可为空。
     */
    private String toolName;

    /**
     * 工具参数快照。
     */
    private Map<String, Object> toolArgs = new LinkedHashMap<>();

    /**
     * 工具执行观察结果（Observation）。
     * 若本步无工具调用可为空。
     */
    private String observation;

    /**
     * 本步最终回答（仅 actionType=ANSWER 时有值）。
     */
    private String finalAnswer;

    /**
     * 步骤执行状态：
     * - PLANNED: 已规划，未执行
     * - EXECUTED: 已成功执行
     * - FAILED: 执行失败
     */
    private String status;

    /**
     * 失败错误信息（status=FAILED 时记录）。
     */
    private String errorMessage;

    /**
     * 步骤开始时间。
     */
    private LocalDateTime startedAt;

    /**
     * 步骤结束时间。
     */
    private LocalDateTime finishedAt;

    public int getStepNo() {
        return stepNo;
    }

    public void setStepNo(int stepNo) {
        this.stepNo = stepNo;
    }

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

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public Map<String, Object> getToolArgs() {
        return toolArgs;
    }

    public void setToolArgs(Map<String, Object> toolArgs) {
        this.toolArgs = toolArgs == null ? new LinkedHashMap<>() : new LinkedHashMap<>(toolArgs);
    }

    public String getObservation() {
        return observation;
    }

    public void setObservation(String observation) {
        this.observation = observation;
    }

    public String getFinalAnswer() {
        return finalAnswer;
    }

    public void setFinalAnswer(String finalAnswer) {
        this.finalAnswer = finalAnswer;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }
}
