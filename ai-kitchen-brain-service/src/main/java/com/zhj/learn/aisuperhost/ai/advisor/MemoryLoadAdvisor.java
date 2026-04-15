package com.zhj.learn.aisuperhost.ai.advisor;

import com.zhj.learn.aisuperhost.domain.WindowTurn;
import com.zhj.learn.aisuperhost.service.MemoryPersistService;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseChatMemoryAdvisor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class MemoryLoadAdvisor implements BaseChatMemoryAdvisor {

    private final MemoryPersistService memoryPersistService;

    public MemoryLoadAdvisor(MemoryPersistService memoryPersistService) {
        this.memoryPersistService = memoryPersistService;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        // 1) 从上下文中解析会话ID；如果调用方没有传，则使用默认会话ID。
        Map<String, Object> chatContext = chatClientRequest.context();
        String sessionId = getConversationId(chatContext, "default");

        // 2) 加载记忆：
        // - summary: 长期记忆摘要（redis优先，mysql回源）
        // - windowTurns: 最近窗口对话（redis优先，mysql回源）
        String summary = memoryPersistService.loadSummary(sessionId);
        List<WindowTurn> windowTurns = memoryPersistService.loadWindowTurns(sessionId);

        // 3) 读取本次请求已有消息，并拆分 system 与非 system。
        // 这样可以确保 system 规则消息始终位于最前，避免被历史消息打乱。
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

        // 4) 将记忆转换为“消息级注入”：
        // - 摘要作为 SystemMessage（只作为上下文参考，不是用户输入）
        // - 窗口历史按 User/Assistant 成对注入，尽量还原真实对话轨迹
        List<Message> memoryMessages = new ArrayList<>();
        if (summary != null && !summary.isBlank()) {
            memoryMessages.add(new SystemMessage("[会话摘要，仅作上下文参考]\n" + summary));
        }
        if (windowTurns != null) {
            for (WindowTurn turn : windowTurns) {
                if (turn == null) {
                    continue;
                }
                String userContent = turn.getUserContent() == null ? "" : turn.getUserContent();
                String assistantContent = turn.getAssistantContent() == null ? "" : turn.getAssistantContent();
                if (!userContent.isBlank()) {
                    memoryMessages.add(new UserMessage(userContent));
                }
                if (!assistantContent.isBlank()) {
                    memoryMessages.add(new AssistantMessage(assistantContent));
                }
            }
        }

        // 5) 重组最终消息顺序：
        // [原 system 规则] -> [记忆消息(摘要+窗口历史)] -> [当前请求消息]
        // 该顺序有利于模型先读取规则，再读取上下文记忆，最后处理当前输入。
        List<Message> processedMessages = new ArrayList<>();
        processedMessages.addAll(systemMessages);
        processedMessages.addAll(memoryMessages);
        processedMessages.addAll(nonSystemMessages);

        // 6) 用重组后的消息构造新请求并返回给后续顾问链/模型调用。
        return chatClientRequest.mutate()
                .prompt(chatClientRequest.prompt().mutate().messages(processedMessages).build())
                .build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        return chatClientResponse;
    }

    @Override
    public int getOrder() {
        return 100;
    }
}
