package com.zhj.learn.aisuperhost.ai.advisor;

import com.zhj.learn.aisuperhost.service.MemoryPersistService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseChatMemoryAdvisor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class MemoryPersistAdvisor implements BaseChatMemoryAdvisor {

    private static final Logger LOG = LoggerFactory.getLogger(MemoryPersistAdvisor.class);

    private final MemoryPersistService memoryPersistService;

    public MemoryPersistAdvisor(MemoryPersistService memoryPersistService) {
        this.memoryPersistService = memoryPersistService;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        return chatClientRequest;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        try {
            Map<String, Object> chatContext = chatClientResponse.context();
            String sessionId = getConversationId(chatContext, "default");
            String userInput = (String) chatContext.getOrDefault("raw_user_input", "");
            String assistantOutput = extractAssistantOutput(chatClientResponse);
            memoryPersistService.persistAfterTurn(sessionId, userInput, assistantOutput);
        } catch (Exception e) {
            LOG.error("memory persist after hook failed", e);
        }
        return chatClientResponse;
    }

    @Override
    public int getOrder() {
        return 200;
    }

    private String extractAssistantOutput(ChatClientResponse response) {
        try {
            String text = response.chatResponse().getResult().getOutput().getText();
            return text == null ? "" : text;
        } catch (Exception e) {
            return "";
        }
    }
}
