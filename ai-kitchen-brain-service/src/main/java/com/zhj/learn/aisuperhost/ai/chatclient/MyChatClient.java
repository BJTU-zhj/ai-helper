package com.zhj.learn.aisuperhost.ai.chatclient;

import com.zhj.learn.aisuperhost.ai.advisor.MemoryLoadAdvisor;
import com.zhj.learn.aisuperhost.ai.advisor.MemoryPersistAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MyChatClient {

    // 创建一个千问对话的ChatClient
    @Bean
    public ChatClient qwenChatClient(@Qualifier("qwenChatModel") ChatModel qwenChatModel,
                                     MemoryLoadAdvisor memoryLoadAdvisor,
                                     MemoryPersistAdvisor memoryPersistAdvisor) {

        return ChatClient.builder(qwenChatModel)
                .defaultAdvisors(memoryLoadAdvisor, memoryPersistAdvisor)
                .build();
    }


}
