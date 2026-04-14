package com.zhj.learn.aisuperhost.ai.chatclient;


import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class MyToolClient {

    //创建一个deepseek的生成摘要的ChatClient
    @Bean
    public ChatClient deepSeekChatClient(@Qualifier("deepSeekChatModel") ChatModel deepSeekChatModel) {
        return ChatClient.builder(deepSeekChatModel)
                .build();
    }

}
