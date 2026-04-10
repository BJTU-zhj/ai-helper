package com.zhj.learn.aihelper.service;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.spring.AiService;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AICodeHelperServiceFactory {

    @Resource
    private ChatLanguageModel qwenChatModel;

    @Bean
    public AICodeHelperService AICodeHelperService() {

        ChatMemory chatMemory= MessageWindowChatMemory.withMaxMessages(10);

        AICodeHelperService aiCodeHelperService= AiServices.builder(AICodeHelperService.class)
                .chatLanguageModel(qwenChatModel)
                .chatMemory(chatMemory)
                .build();
        return aiCodeHelperService;
    }

}
