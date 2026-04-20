package com.zhj.learn.aihelper.controller;

import com.zhj.learn.aicommon.VO.CommonResp;
import com.zhj.learn.aihelper.dto.UtilityChatRequest;
import com.zhj.learn.aihelper.dto.UtilityChatResponse;
import com.zhj.learn.aihelper.service.UtilityAgentService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/utility")
public class UtilityChatController {

    @Resource
    private UtilityAgentService utilityAgentService;

    @PostMapping("/chat")
    public CommonResp<UtilityChatResponse> chat(@RequestBody UtilityChatRequest request) {
        return new CommonResp<>(utilityAgentService.chat(request));
    }
}
