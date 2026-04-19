package com.zhj.learn.aisuperhost.config;

import io.netty.util.concurrent.ThreadPerTaskExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class RagExecutorConfig {

    @Bean("ragRecallExecutor")
    public Executor ragRecallExecutor() {
        ThreadPoolTaskExecutor ex=new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(4);
        ex.setMaxPoolSize(8);
        ex.setQueueCapacity(100);
        ex.setThreadNamePrefix("rag-recall-");
        ex.initialize();
        return ex;
    }

}
