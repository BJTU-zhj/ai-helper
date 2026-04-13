package com.zhj.learn.aisuperhost.ai.chatclient;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.BaseChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MyChatClient {

    @Resource
    private BaseChatMemoryAdvisor myMessageChatMemoryAdvisor;


    // 创建一个千问对话的ChatClient
    @Bean
    public ChatClient qwenChatClient(@Qualifier("qwenChatModel") ChatModel qwenChatModel) {

        return ChatClient.builder(qwenChatModel)
                .defaultAdvisors(myMessageChatMemoryAdvisor)
                .build();
    }

}
