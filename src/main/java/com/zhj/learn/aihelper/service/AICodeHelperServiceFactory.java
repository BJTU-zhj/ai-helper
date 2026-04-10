package com.zhj.learn.aihelper.service;

import com.zhj.learn.aihelper.service.tools.ToolsExample;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.spring.AiService;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AICodeHelperServiceFactory {

    @Resource
    private ChatLanguageModel qwenChatModel;

    @Resource
    private ContentRetriever contentRetriever;

    @Resource
    private ToolsExample toolsExample;

    @Bean
    public AICodeHelperService aiCodeHelperService() {

        ChatMemory chatMemory= MessageWindowChatMemory.withMaxMessages(10);

        AICodeHelperService aiCodeHelperService= AiServices.builder(AICodeHelperService.class)
                .chatLanguageModel(qwenChatModel)
                .chatMemory(chatMemory)
                .build();
        return aiCodeHelperService;
    }

    @Bean
    public AICodeHelperService aiCodeHelperServiceWithRag(){

        ChatMemory chatMemory= MessageWindowChatMemory.withMaxMessages(10);

        return AiServices.builder(AICodeHelperService.class)
                .chatLanguageModel(qwenChatModel)
                .chatMemory(chatMemory)
                .contentRetriever(contentRetriever)
                .build();
    }

    @Bean
    public AICodeHelperService aiCodeHelperServiceWithTools(){

        ChatMemory chatMemory= MessageWindowChatMemory.withMaxMessages(10);

        return AiServices.builder(AICodeHelperService.class)
                .chatLanguageModel(qwenChatModel)
                .chatMemory(chatMemory)
                .tools(toolsExample)
                .build();
    }

}
