package com.zhj.learn.aisuperhost.ai.chatclient;

import com.zhj.learn.aisuperhost.ai.advisor.MemoryLoadAdvisor;
import com.zhj.learn.aisuperhost.ai.advisor.MemoryPersistAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MyChatClient {

    // 创建一个千问对话的ChatClient
    @Bean
    public ChatClient qwenChatClient(@Qualifier("qwenChatModel") ChatModel qwenChatModel,
                                     MemoryLoadAdvisor memoryLoadAdvisor,
                                     RetrievalAugmentationAdvisor retrievalAugmentationAdvisor,
                                     MemoryPersistAdvisor memoryPersistAdvisor,
                                     @Qualifier("myMcpToolCallbackProvider") SyncMcpToolCallbackProvider myMcpToolCallbackProvider) {

        return ChatClient.builder(qwenChatModel)
                // 顺序：记忆加载 -> RAG 检索增强 -> 记忆持久化
                .defaultAdvisors(memoryLoadAdvisor, retrievalAugmentationAdvisor, memoryPersistAdvisor)
                .defaultToolCallbacks(myMcpToolCallbackProvider)
                .build();
    }


}
