package com.zhj.learn.aisuperhost.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "rag.retrieval")
public class RagConfig {

    private int keywordTopK;

    private int vectorTopK;

    private int finalTopK;

    private int rrfK;

    private  double minScore;
}
