package com.zhj.learn.aihelper.controller;

import com.zhj.learn.aicommon.VO.CommonResp;
import com.zhj.learn.aihelper.service.rag.KnowledgeIngestionService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/aihelper/rag")
public class RagIngestionController {

    @Resource
    private KnowledgeIngestionService knowledgeIngestionService;

    @PostMapping("/ingest")
    public CommonResp<String> ingestDocx() {
        return new CommonResp<>(knowledgeIngestionService.ingestAllDocx());
    }
}

