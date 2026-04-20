package com.zhj.learn.aisuperhost.ai.react.enums;

/**
 * ReAct 每一步规划允许的动作类型。
 *
 * <p>约束：
 * 1. ANSWER: 本轮直接给用户最终答复，不再调用工具。
 * 2. TOOL_CALL: 本轮需要调用一个工具，执行后进入下一轮规划。
 */
public enum ReactActionType {
    ANSWER,
    TOOL_CALL
}
