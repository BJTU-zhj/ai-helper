package com.zhj.learn.aisuperhost.ai.advisor;

import com.zhj.learn.aisuperhost.domain.WindowTurn;
import com.zhj.learn.aisuperhost.service.MemoryPersistService;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseChatMemoryAdvisor;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class MemoryLoadAdvisor implements BaseChatMemoryAdvisor {

    private final MemoryPersistService memoryPersistService;

    @Value("classpath:template/memory-context.st")
    private Resource memoryContext;

    public MemoryLoadAdvisor(MemoryPersistService memoryPersistService) {
        this.memoryPersistService = memoryPersistService;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        Map<String, Object> chatContext = chatClientRequest.context();
        String sessionId = getConversationId(chatContext, "default");

        String summary = memoryPersistService.loadSummary(sessionId);
        List<WindowTurn> windowTurns = memoryPersistService.loadWindowTurns(sessionId);
        String windowTurnsText = memoryPersistService.formatWindowTurns(windowTurns);

        Map<String, Object> vars = new HashMap<>();
        vars.put("summary", summary);
        vars.put("window_turns", windowTurnsText);

        PromptTemplate memoryPromptTemplate = new PromptTemplate(memoryContext);
        String memoryBlock = memoryPromptTemplate.render(vars);

        Map<String, Object> newContext = new HashMap<>(chatContext);
        newContext.put("memory_block", memoryBlock);
        return chatClientRequest.mutate().context(newContext).build();
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
