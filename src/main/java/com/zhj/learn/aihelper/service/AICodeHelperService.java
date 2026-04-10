package com.zhj.learn.aihelper.service;


import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.spring.AiService;

//@AiService
public interface AICodeHelperService {

    @SystemMessage(fromResource ="prompt/AiCodeSystemPrompt.st" )
    String chat(String message);
}
