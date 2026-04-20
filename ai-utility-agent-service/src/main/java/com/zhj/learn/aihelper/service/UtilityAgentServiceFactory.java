package com.zhj.learn.aihelper.service;

import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UtilityAgentServiceFactory {

    @Resource
    private ChatModel myQwenChatModel;

    @Value("${app.utility.mcp-enabled:false}")
    private boolean utilityMcpEnabled;

    @Bean
    public UtilityAgentAiService utilityAgentAiService(
            @Qualifier("mcpToolProvider") ObjectProvider<McpToolProvider> mcpToolProviderProvider) {
        AiServices<UtilityAgentAiService> builder = AiServices.builder(UtilityAgentAiService.class)
                .chatModel(myQwenChatModel)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId)
                        .maxMessages(10)
                        .build());

        McpToolProvider mcpToolProvider = utilityMcpEnabled ? mcpToolProviderProvider.getIfAvailable() : null;
        if (mcpToolProvider != null) {
            builder.toolProviders(mcpToolProvider);
        }
        return builder.build();
    }
}
