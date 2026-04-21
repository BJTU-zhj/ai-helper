package com.zhj.learn.aisuperhost.ai.react.advisor;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseChatMemoryAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ReAct Planner 专用上下文顾问（消息级注入）。
 *
 * <p>这个 Advisor 的核心职责是：
 * 1. 仅在 ReAct 规划链路中生效（通过 context 中的 react_mode 标记识别）。
 * 2. 将本轮 ReAct 已执行步骤摘要（thought/action/observation）注入到消息序列中，
 *    供下一轮规划参考。
 * 3. 不改写长期会话记忆，只改写“本次请求发送给模型的消息列表”。
 *
 * <p>为什么选择“消息级注入”而不是字符串拼接 Prompt：
 * - 可以更稳定地与 MemoryLoadAdvisor 共存，不需要手工拼接历史文本。
 * - 对模型来说语义更清晰：系统约束、历史对话、步骤摘要各司其职。
 */
@Component
public class PlannerStepContextAdvisor implements BaseChatMemoryAdvisor {

    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        // 只在 ReAct 模式下注入步骤历史，避免影响普通聊天调用链路。
        Map<String, Object> context = chatClientRequest.context();
        boolean reactMode = Boolean.TRUE.equals(context.get("react_mode"));
        if (!reactMode) {
            return chatClientRequest;
        }

        // planner_step_history 由 ReActAgentService 在每一步规划前传入。
        // 为空时直接跳过，保持原消息不变。
        String stepHistory = (String) context.getOrDefault("planner_step_history", "");
        if (!StringUtils.hasText(stepHistory)) {
            return chatClientRequest;
        }

        // 将消息拆分为 system 与 non-system 两段：
        // - system 仍保持在最前面，保证规则约束优先。
        // - non-system 中包含历史对话、当前 user 消息等。
        List<Message> originalMessages = chatClientRequest.prompt().getInstructions();
        List<Message> systemMessages = new ArrayList<>();
        List<Message> nonSystemMessages = new ArrayList<>();
        for (Message message : originalMessages) {
            if (message instanceof SystemMessage) {
                systemMessages.add(message);
            } else {
                nonSystemMessages.add(message);
            }
        }

        List<Message> processed = new ArrayList<>();
        processed.addAll(systemMessages);
        if (!nonSystemMessages.isEmpty() && nonSystemMessages.get(nonSystemMessages.size() - 1) instanceof UserMessage) {
            // 关键策略：
            // 若最后一条是 user 消息，则必须保证它依然在末尾。
            // 这样可避免“步骤摘要”压到最后，导致模型把摘要当作最近输入而偏离当前问题。
            processed.addAll(nonSystemMessages.subList(0, nonSystemMessages.size() - 1));
            processed.add(new AssistantMessage("[ReAct步骤历史摘要，仅作规划参考]\n" + stepHistory.trim()));
            processed.add(nonSystemMessages.get(nonSystemMessages.size() - 1));
        } else {
            // 非标准场景兜底：
            // 如果末尾不是 user（例如调用链插入了其他消息），则把摘要放在最后。
            processed.addAll(nonSystemMessages);
            processed.add(new AssistantMessage("[ReAct步骤历史摘要，仅作规划参考]\n" + stepHistory.trim()));
        }

        // 返回“重写后的 Prompt 消息列表”，其余上下文参数原样保留。
        return chatClientRequest.mutate()
                .prompt(chatClientRequest.prompt().mutate().messages(processed).build())
                .build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        return chatClientResponse;
    }

    @Override
    public int getOrder() {
        // 在 MemoryLoadAdvisor(100) 之后执行：
        // 先加载会话记忆，再注入步骤记忆，避免步骤记忆被覆盖。
        return 120;
    }
}
