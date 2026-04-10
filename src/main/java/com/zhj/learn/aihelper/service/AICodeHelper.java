package com.zhj.learn.aihelper.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class AICodeHelper {

    @Resource
    private ChatLanguageModel qwenChatModel;

    @Resource
    private AICodeHelperService aiCodeHelperService;

    @Resource
    private AICodeHelperService aiCodeHelperServiceWithRag;

    //简单对话
    public String chat(String message) {
        UserMessage userMessage = UserMessage.from(message);
        Response<AiMessage> response = qwenChatModel.generate(List.of(userMessage));
        AiMessage aiMessage = response.content();
        log.info("AI输出：" + aiMessage);
        return aiMessage.text();
    }

    //多模态
    public String chatWithImage(UserMessage userMessage) {
        Response<AiMessage> response = qwenChatModel.generate(List.<ChatMessage>of(userMessage));
        log.info("AI输出：" + response.content().text());
        return response.content().text();
    }

    //用ai servic来抽象chat过程
    public String chatWithService(String message) {
        return aiCodeHelperService.chat(message);
    }

    //使用 rag来增强会话质量,使用ai service
    public String chatWithRag(String message){
        return aiCodeHelperServiceWithRag.chat(message);
    }
}
