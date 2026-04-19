package com.zhj.learn.aisuperhost.ai.chatclient;


import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class MyToolClient {

    //创建一个deepseek的生成摘要、查询重构的ChatClient
    @Bean
    public ChatClient deepSeekChatClient(@Qualifier("deepSeekChatModel") ChatModel deepSeekChatModel) {
        return ChatClient.builder(deepSeekChatModel)
                .build();
    }

    @Bean
    public ChatClient qwenRerankChatClient(@Qualifier("qwenRerankChatModel") ChatModel qwenRerankChatModel) {
        return ChatClient.builder(qwenRerankChatModel)
                .build();
    }


}
