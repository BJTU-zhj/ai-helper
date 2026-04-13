package com.zhj.learn.aisuperhost.ai.memory;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MyChatMemory {

    @Resource
    private ChatMemoryRepository myInMemoryChatMemoryRepository;

    //创建一个会话历史策略
    @Bean
    public ChatMemory myInMemoryChatMemory() {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(myInMemoryChatMemoryRepository)
                .maxMessages(10)
                .build();
    }

}
