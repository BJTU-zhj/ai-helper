package com.zhj.learn.aisuperhost.ai.chatclient;

import com.zhj.learn.aisuperhost.ai.advisor.MemoryLoadAdvisor;
import com.zhj.learn.aisuperhost.ai.advisor.MemoryPersistAdvisor;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.BaseChatMemoryAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MyChatClient {

    @Resource
    private BaseChatMemoryAdvisor myMessageChatMemoryAdvisor;

    @Resource
    private MemoryLoadAdvisor memoryLoadAdvisor;

    @Resource
    private MemoryPersistAdvisor memoryPersistAdvisor;


    // 创建一个千问对话的ChatClient
    @Bean
    public ChatClient qwenChatClient(@Qualifier("qwenChatModel") ChatModel qwenChatModel) {

        return ChatClient.builder(qwenChatModel)
                .defaultAdvisors(memoryLoadAdvisor, myMessageChatMemoryAdvisor, memoryPersistAdvisor)
                .build();
    }

    //创建一个deepseek的生成摘要的ChatClient
    @Bean
    public ChatClient deepSeekChatClient(@Qualifier("deepSeekChatModel") ChatModel deepSeekChatModel) {
        return ChatClient.builder(deepSeekChatModel)
                .build();
    }

}
