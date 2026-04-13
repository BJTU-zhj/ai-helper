package com.zhj.learn.aisuperhost.ai.memory;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MyChatMemoryRepository {

    //创建一个内存对话存储仓库
    @Bean
    public ChatMemoryRepository myInMemoryChatMemoryRepository() {
        return new InMemoryChatMemoryRepository();
    }


}
