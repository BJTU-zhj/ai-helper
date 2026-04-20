package com.zhj.learn.aisuperhost.ai.react.dto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ReAct 中单次工具调用的结构化描述。
 *
 * <p>设计说明：
 * 1. toolName 必填，且应来自系统维护的工具白名单。
 * 2. toolArgs 是工具参数集合，使用 key-value 形式承载，便于后续统一序列化为 JSON。
 * 3. 本对象只描述“要调用什么”，不包含调用结果（Observation）。
 */
public class ReactToolCall {

    private String toolName;

    private Map<String, Object> toolArgs = new LinkedHashMap<>();

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public Map<String, Object> getToolArgs() {
        return toolArgs == null ? Collections.emptyMap() : toolArgs;
    }

    public void setToolArgs(Map<String, Object> toolArgs) {
        this.toolArgs = toolArgs == null ? new LinkedHashMap<>() : new LinkedHashMap<>(toolArgs);
    }
}
