package com.zhj.learn.aihelper.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface UtilityAgentAiService {

    @SystemMessage(fromResource ="prompt/UtilitySystemPrompt.st" )
    String chat(@MemoryId String memoryId, @UserMessage String message);
}
