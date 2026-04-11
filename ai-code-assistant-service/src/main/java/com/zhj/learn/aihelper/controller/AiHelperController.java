package com.zhj.learn.aihelper.controller;

import com.zhj.learn.aihelper.service.AICodeHelper;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/aihelper")
public class AiHelperController {

    @Resource
    private AICodeHelper aiCodeHelper;


    //测试一般chat
    @GetMapping("/chat/{message}")
    public String chat(@PathVariable String message) {
        return aiCodeHelper.chatWithMcpLocal(message);
    }

    //测试流式响应
    @GetMapping(value = "/chatStream/{memoryId}/{message}", produces = "text/html;charset=UTF-8")
    public Flux<String> chatStream(@PathVariable String memoryId, @PathVariable String message){
        return aiCodeHelper.chatWithStream(memoryId,message);
    }

}
