package com.zhj.learn.aisuperhost.service;

import jakarta.annotation.Resource;
import jakarta.annotation.PostConstruct;
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
public class AiService {

    @Resource
    private ChatClient qwenChatClient;

    @Value("classpath:template/system-prompt-v1.st")
    private org.springframework.core.io.Resource systemPromptTemplateResource;

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
        return qwenChatClient.prompt()
                .system(systemPromptTemplate)
                .user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, memoryId)
                        .param("raw_user_input", message))
                .call().content();
    }
}
