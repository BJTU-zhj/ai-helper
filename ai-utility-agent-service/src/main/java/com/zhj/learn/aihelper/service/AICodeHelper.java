package com.zhj.learn.aihelper.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

@Service
@Slf4j
public class AICodeHelper {

    @Resource
    private ChatModel qwenChatModel;

    @Resource
    private AICodeHelperService aiCodeHelperService;

    @Resource
    private AICodeHelperService aiCodeHelperServiceWithRag;

    @Resource
    private AICodeHelperService aiCodeHelperServiceWithTools;

    @Resource
    private AICodeHelperService aiCodeHelperServiceWithMcp;

    @Resource
    private AICodeHelperService aiCodeHelperServiceWithMcpLocal;

    @Resource
    private AICodeHelperService aiCodeHelperServiceStream;

    //简单对话
    public String chat(String message) {
        UserMessage userMessage = UserMessage.from(message);
        ChatResponse response = qwenChatModel.chat(List.of(userMessage));
        AiMessage aiMessage = response.aiMessage();
        log.info("AI输出：" + aiMessage);
        return aiMessage.text();
    }

    //多模态
    public String chatWithImage(UserMessage userMessage) {
        ChatResponse response = qwenChatModel.chat(List.<ChatMessage>of(userMessage));
        log.info("AI输出：" + response.aiMessage().text());
        return response.aiMessage().text();
    }

    //用ai servic来抽象chat过程
    public String chatWithService(String message) {
        return aiCodeHelperService.chat(message);
    }

    //使用 rag来增强会话质量,使用ai service
    public String chatWithRag(String message){
        return aiCodeHelperServiceWithRag.chat(message);
    }

    //使用 ai service+tools
    public String chatWithTools(String message){
        return aiCodeHelperServiceWithTools.chat(message);
    }

    //使用高德mcp获取真实天气
    public  String chatWithMcp(String message){
        return aiCodeHelperServiceWithMcp.chat(message);
    }

    //使用高德mcp获取真实天气-本地server
    public  String chatWithMcpLocal(String message){
        return aiCodeHelperServiceWithMcpLocal.chat(message);
    }

    //体验响应式，流式+会话记忆分割
    public Flux<String> chatWithStream(String memoryId, String message){
        return aiCodeHelperServiceStream.chatStream(memoryId,message)
                .doOnError(e -> log.error("流式响应失败, message={}", message, e))
                .onErrorResume(e -> Flux.just("流式调用失败: " + e.getMessage()));
    }
}
