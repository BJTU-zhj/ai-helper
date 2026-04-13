package com.zhj.learn.aisuperhost.controller;

import com.zhj.learn.aicommon.VO.CommonResp;
import com.zhj.learn.aisuperhost.service.AiService;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/brain")
public class SessionController {

    @Resource
    private AiService aiService;


    @GetMapping("/chat/{sessionId}/{message}")
    public CommonResp<String> chat(@PathVariable String sessionId, @PathVariable String message){
        return new CommonResp<>(aiService.chat(sessionId, message));
    }

}
