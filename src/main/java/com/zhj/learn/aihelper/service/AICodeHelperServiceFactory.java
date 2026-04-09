package com.zhj.learn.aihelper.service;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

//@Configuration
public class AICodeHelperServiceFactory {

    @Resource
    private ChatLanguageModel qwenChatModel;

    @Bean
    public AICodeHelperService AICodeHelperService() {
        return AiServices.create(AICodeHelperService.class, qwenChatModel);
    }

}
