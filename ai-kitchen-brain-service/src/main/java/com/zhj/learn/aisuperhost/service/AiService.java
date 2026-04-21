package com.zhj.learn.aisuperhost.service;

import com.zhj.learn.aisuperhost.ai.react.service.ReactAgentService;
import com.zhj.learn.aisuperhost.ai.react.dto.ReactStreamEvent;
import jakarta.annotation.Resource;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 专注于构造模型服务
 */


@Service
@Slf4j
public class AiService {

    @Resource
    private ChatClient qwenChatClient;

    @Resource
    private ReactAgentService reactAgentService;

    @Value("classpath:template/system-prompt-v1.st")
    private org.springframework.core.io.Resource systemPromptTemplateResource;

    @Value("${app.react.enabled:false}")
    private boolean reactEnabled;

    private final ExecutorService streamExecutor = Executors.newCachedThreadPool();

    private String systemPromptTemplate;

    @PostConstruct
    public void initSystemPromptTemplate() throws IOException {
        this.systemPromptTemplate = StreamUtils.copyToString(
                systemPromptTemplateResource.getInputStream(),
                StandardCharsets.UTF_8
        );
    }

    //多轮对话记忆
    public String chat(String memoryId, String message){
        if (reactEnabled) {
            try {
                return reactAgentService.run(memoryId, message);
            } catch (Exception e) {
                // ReAct 失败时自动降级为原有直答链路，避免主流程不可用。
                log.error("ReAct agent failed, fallback to direct chat. memoryId={}", memoryId, e);
            }
        }
        return directChat(memoryId, message);
    }

    /**
     * 流式聊天接口：
     * - ReAct 开启时推送 Agent 步骤事件，便于前端展示执行过程。
     * - 普通直答时推送 answer_delta，便于前端逐字展示回答。
     */
    public SseEmitter streamChat(String memoryId, String message) {
        SseEmitter emitter = new SseEmitter(0L);
        streamExecutor.submit(() -> {
            try {
                sendEvent(emitter, "start", eventPayload("stream started"));
                if (reactEnabled) {
                    reactAgentService.run(memoryId, message, event -> sendReactEvent(emitter, event));
                } else {
                    streamDirectChat(memoryId, message, emitter);
                }
                emitter.complete();
            } catch (Exception e) {
                log.error("stream chat failed. memoryId={}", memoryId, e);
                try {
                    sendEvent(emitter, "error", errorPayload(e.getMessage()));
                } catch (Exception sendException) {
                    log.warn("send stream error event failed. memoryId={}", memoryId, sendException);
                }
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    @PreDestroy
    public void shutdownStreamExecutor() {
        streamExecutor.shutdown();
    }

    private String directChat(String memoryId, String message) {
        return qwenChatClient.prompt()
                .system(systemPromptTemplate)
                .user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, memoryId)
                        .param("raw_user_input", message))
                .call().content();
    }

    private void streamDirectChat(String memoryId, String message, SseEmitter emitter) {
        qwenChatClient.prompt()
                .system(systemPromptTemplate)
                .user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, memoryId)
                        .param("raw_user_input", message))
                .stream()
                .content()
                .doOnNext(chunk -> sendEvent(emitter, "answer_delta", deltaPayload(chunk)))
                .doOnComplete(() -> sendEvent(emitter, "done", eventPayload("stream finished")))
                .blockLast();
    }

    private void sendReactEvent(SseEmitter emitter, ReactStreamEvent event) {
        String eventName = event.getType();
        if (eventName == null || eventName.isBlank()) {
            eventName = "react_event";
        }
        sendEvent(emitter, eventName, event);
    }

    private void sendEvent(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (IOException e) {
            throw new IllegalStateException("send SSE event failed: " + name, e);
        }
    }

    private Map<String, Object> eventPayload(String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", message);
        payload.put("timestamp", LocalDateTime.now());
        return payload;
    }

    private Map<String, Object> deltaPayload(String delta) {
        Map<String, Object> payload = eventPayload("answer delta");
        payload.put("delta", delta);
        return payload;
    }

    private Map<String, Object> errorPayload(String errorMessage) {
        Map<String, Object> payload = eventPayload("stream error");
        payload.put("errorMessage", errorMessage);
        return payload;
    }
}
