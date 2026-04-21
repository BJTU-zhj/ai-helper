package com.zhj.learn.aisuperhost.controller;

import com.zhj.learn.aicommon.VO.CommonResp;
import com.zhj.learn.aisuperhost.service.AiService;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/brain")
public class SessionController {

    @Resource
    private AiService aiService;


    @GetMapping("/chat/{sessionId}/{message}")
    public CommonResp<String> chat(@PathVariable String sessionId, @PathVariable String message){
        return new CommonResp<>(aiService.chat(sessionId, message));
    }

    @GetMapping(value = "/chat/stream/{sessionId}/{message}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@PathVariable String sessionId, @PathVariable String message) {
        return aiService.streamChat(sessionId, message);
    }

}
