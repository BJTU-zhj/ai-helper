package com.zhj.learn.aisuperhost.service;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;

@Service
public class AiService {

    @Resource
    private ChatClient qwenChatClient;

    //多轮对话记忆
    public String chat(String memoryId, String message){
        return qwenChatClient.prompt().user(message)
                .advisors(a->a.param(ChatMemory.CONVERSATION_ID, memoryId))
                .call().content();
    }

}
