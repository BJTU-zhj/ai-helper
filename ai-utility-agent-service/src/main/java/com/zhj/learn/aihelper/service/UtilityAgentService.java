package com.zhj.learn.aihelper.service;

import com.zhj.learn.aihelper.dto.UtilityChatRequest;
import com.zhj.learn.aihelper.dto.UtilityChatResponse;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
public class UtilityAgentService {

    @Resource(name = "utilityAgentAiService")
    private UtilityAgentAiService utilityAgentAiService;

    public UtilityChatResponse chat(UtilityChatRequest request) {
        if (request == null || !StringUtils.hasText(request.getMessage())) {
            throw new IllegalArgumentException("message must not be blank");
        }
        String sessionId = StringUtils.hasText(request.getSessionId())
                ? request.getSessionId()
                : UUID.randomUUID().toString();
        String answer = utilityAgentAiService.chat(sessionId, request.getMessage());
        return new UtilityChatResponse(sessionId, answer);
    }
}
