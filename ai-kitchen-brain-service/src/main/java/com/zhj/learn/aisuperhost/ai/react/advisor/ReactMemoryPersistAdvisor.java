package com.zhj.learn.aisuperhost.ai.react.advisor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhj.learn.aisuperhost.ai.react.dto.ReactPlan;
import com.zhj.learn.aisuperhost.ai.react.enums.ReactActionType;
import com.zhj.learn.aisuperhost.service.MemoryPersistService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseChatMemoryAdvisor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * ReAct 记忆持久化顾问（防污染版）。
 *
 * <p>设计目标：
 * 1. 仅处理 ReAct 规划调用链，普通聊天链路完全不受影响。
 * 2. 只在模型输出为“最终回答计划”（actionType=ANSWER 且 finalAnswer 非空）时，
 *    才把“用户问题 + 最终答案”写入长期会话记忆。
 * 3. 任何中间步骤（THINK/TOOL）都不落库，防止步骤轨迹污染业务会话记忆。
 *
 * <p>注意：
 * - 这个 Advisor 假设模型输出遵循 ReactPlan JSON 协议。
 * - 如果输出不是合法 JSON，或不满足 ANSWER 条件，会跳过持久化并记录 warn 日志。
 */
@Component
@Slf4j
public class ReactMemoryPersistAdvisor implements BaseChatMemoryAdvisor {

    private final MemoryPersistService memoryPersistService;

    private final ObjectMapper objectMapper;

    public ReactMemoryPersistAdvisor(MemoryPersistService memoryPersistService, ObjectMapper objectMapper) {
        this.memoryPersistService = memoryPersistService;
        this.objectMapper = objectMapper;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        // 持久化逻辑仅在 after 阶段执行，before 阶段不做任何修改。
        return chatClientRequest;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        // 只对 ReAct 规划链路生效。
        Map<String, Object> context = chatClientResponse.context();
        boolean reactMode = Boolean.TRUE.equals(context.get("react_mode"));
        if (!reactMode) {
            return chatClientResponse;
        }

        // 优先读取 react_user_input；若缺失则回退 raw_user_input（兼容旧链路）。
        String userInput = (String) context.getOrDefault("react_user_input", "");
        if (!StringUtils.hasText(userInput)) {
            userInput = (String) context.getOrDefault("raw_user_input", "");
        }

        // 会话ID必须稳定，否则无法正确写入同一会话的长期记忆。
        String sessionId = getConversationId(context, "default");
        // 获取模型原始输出文本，按 ReactPlan 协议解析。
        String modelText = extractAssistantOutput(chatClientResponse);
        try {
            ReactPlan plan = objectMapper.readValue(modelText, ReactPlan.class);
            // 仅当规划结果明确是“最终回答”时持久化，
            // 中间步骤（如 TOOL）不会写入，避免污染会话记忆。
            if (plan.getActionType() == ReactActionType.ANSWER && StringUtils.hasText(plan.getFinalAnswer())) {
                memoryPersistService.persistAfterTurn(sessionId, userInput, plan.getFinalAnswer());
                log.info("ReAct final answer persisted. sessionId={}", sessionId);
            }
        } catch (Exception e) {
            // 解析失败通常代表模型输出不是 ReactPlan JSON（比如异常文本、空串等）。
            // 这里选择跳过而不是抛错，避免影响主链路响应。
            log.warn("ReactMemoryPersistAdvisor skipped due to non-plan output. sessionId={}", sessionId);
        }

        return chatClientResponse;
    }

    @Override
    public int getOrder() {
        // 保持在步骤注入之后执行，确保读取到最终模型输出再做持久化决策。
        return 180;
    }

    private String extractAssistantOutput(ChatClientResponse response) {
        try {
            // 从 ChatResponse 中提取助手文本；遇到空结构时回退为空串，避免 NPE。
            String text = response.chatResponse().getResult().getOutput().getText();
            return text == null ? "" : text;
        } catch (Exception e) {
            return "";
        }
    }
}
