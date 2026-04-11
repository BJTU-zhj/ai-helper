package com.zhj.learn.aihelper.service;

import com.zhj.learn.aihelper.service.mcp.GaoDeMcpConfig;
import com.zhj.learn.aihelper.service.tools.ToolsExample;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AICodeHelperServiceFactory {

    @Resource
    private ChatModel myQwenChatModel;

    @Resource
    private StreamingChatModel myStreamingQwenChatModel;

    @Resource
    private ContentRetriever contentRetriever;

    @Resource
    private ToolsExample toolsExample;

    @Resource
    private McpToolProvider mcpToolProvider;

    @Resource
    private McpToolProvider mcpToolProviderLocal;

    @Bean
    public AICodeHelperService aiCodeHelperService() {

        ChatMemory chatMemory= MessageWindowChatMemory.withMaxMessages(10);

        AICodeHelperService aiCodeHelperService= AiServices.builder(AICodeHelperService.class)
                .chatModel(myQwenChatModel)
                .chatMemory(chatMemory)
                .build();
        return aiCodeHelperService;
    }

    @Bean
    public AICodeHelperService aiCodeHelperServiceWithRag(){

        ChatMemory chatMemory= MessageWindowChatMemory.withMaxMessages(10);

        return AiServices.builder(AICodeHelperService.class)
                .chatModel(myQwenChatModel)
                .chatMemory(chatMemory)
                .contentRetriever(contentRetriever)
                .build();
    }

    @Bean
    public AICodeHelperService aiCodeHelperServiceWithTools(){

        ChatMemory chatMemory= MessageWindowChatMemory.withMaxMessages(10);

        return AiServices.builder(AICodeHelperService.class)
                .chatModel(myQwenChatModel)
                .chatMemory(chatMemory)
                .tools(toolsExample)
                .build();
    }

    @Bean
    public AICodeHelperService aiCodeHelperServiceWithMcp(){
        ChatMemory chatMemory= MessageWindowChatMemory.withMaxMessages(10);
        return AiServices.builder(AICodeHelperService.class)
                .chatMemory(chatMemory)
                .chatModel(myQwenChatModel)
                .toolProviders(mcpToolProvider)
                .build();
    }

    @Bean
    public AICodeHelperService aiCodeHelperServiceWithMcpLocal(){
        ChatMemory chatMemory= MessageWindowChatMemory.withMaxMessages(10);
        return AiServices.builder(AICodeHelperService.class)
                .chatMemory(chatMemory)
                .chatModel(myQwenChatModel)
                .toolProviders(mcpToolProviderLocal)
                .build();
    }

    //体验响应式，流式
    @Bean
    public AICodeHelperService aiCodeHelperServiceStream(){

        return AiServices.builder(AICodeHelperService.class)
                .chatModel(myQwenChatModel)
                .streamingChatModel(myStreamingQwenChatModel)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId).maxMessages(10).build())
                .toolProvider(mcpToolProvider)
                .build();
    }


}



