package com.zhj.learn.aihelper.service;


import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import reactor.core.publisher.Flux;

//@AiService
public interface AICodeHelperService {

    //同步方法
    @SystemMessage(fromResource ="prompt/AiCodeSystemPrompt.st" )
    String chat(String message);

    //响应式方法
    @SystemMessage(fromResource ="prompt/AiCodeSystemPrompt.st" )
    Flux<String> chatStream(@MemoryId String memoryId,@UserMessage String message);
}
