package com.zhj.learn.aisuperhost.ai.advisor;

import com.zhj.learn.aisuperhost.ai.memory.MyChatMemory;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.BaseChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MyInMemoryAdvisor {


    @Resource
    private ChatMemory myInMemoryChatMemory;

    // 创建一个会话历史顾问
    @Bean
    public BaseChatMemoryAdvisor myMessageChatMemoryAdvisor() {
        return MessageChatMemoryAdvisor.builder(myInMemoryChatMemory).build();
    }


}
