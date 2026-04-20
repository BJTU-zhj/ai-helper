package com.zhj.learn.aisuperhost.service;

import com.zhj.learn.aisuperhost.ai.react.service.ReactAgentService;
import jakarta.annotation.Resource;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

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

    private String directChat(String memoryId, String message) {
        return qwenChatClient.prompt()
                .system(systemPromptTemplate)
                .user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, memoryId)
                        .param("raw_user_input", message))
                .call().content();
    }
}
