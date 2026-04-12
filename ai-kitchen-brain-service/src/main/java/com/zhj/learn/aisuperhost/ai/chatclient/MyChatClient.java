package com.zhj.learn.aisuperhost.ai.chatclient;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MyChatClient {

    @Resource
    private ChatModel qwenChatModel;


    // 创建一个千问对话的ChatClient
    @Bean
    public ChatClient qwenChatClient(ChatModel qwenChatModel) {
        return ChatClient.builder(qwenChatModel)
                .build();
    }

}
