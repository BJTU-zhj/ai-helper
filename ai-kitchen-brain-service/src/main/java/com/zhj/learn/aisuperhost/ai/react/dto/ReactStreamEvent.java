package com.zhj.learn.aisuperhost.ai.react.dto;

import com.zhj.learn.aisuperhost.ai.react.enums.ReactActionType;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ReAct 流式事件。
 *
 * <p>后端通过 SSE 将该对象推给前端，前端可以据此展示 Agent 的规划、工具调用、
 * 工具结果、最终回答和异常状态。
 */
public class ReactStreamEvent {

    private String type;

    private String message;

    private Integer stepNo;

    private ReactActionType actionType;

    private String thoughtSummary;

    private String toolName;

    private Map<String, Object> toolArgs = new LinkedHashMap<>();

    private String observation;

    private String finalAnswer;

    private String errorMessage;

    private String reason;

    private LocalDateTime timestamp = LocalDateTime.now();

    public static ReactStreamEvent of(String type, String message) {
        ReactStreamEvent event = new ReactStreamEvent();
        event.setType(type);
        event.setMessage(message);
        return event;
    }

    public static ReactStreamEvent fromStep(String type, ReactStep step) {
        ReactStreamEvent event = new ReactStreamEvent();
        event.setType(type);
        event.setStepNo(step.getStepNo());
        event.setActionType(step.getActionType());
        event.setThoughtSummary(step.getThoughtSummary());
        event.setToolName(step.getToolName());
        event.setToolArgs(step.getToolArgs());
        event.setObservation(step.getObservation());
        event.setFinalAnswer(step.getFinalAnswer());
        event.setErrorMessage(step.getErrorMessage());
        return event;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Integer getStepNo() {
        return stepNo;
    }

    public void setStepNo(Integer stepNo) {
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

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}

