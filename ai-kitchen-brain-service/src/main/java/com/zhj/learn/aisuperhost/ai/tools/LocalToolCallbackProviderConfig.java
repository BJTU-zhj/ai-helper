package com.zhj.learn.aisuperhost.ai.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

/**
 * 本地 Tool 统一注册配置。
 *
 * <p>这里负责把项目内自己实现的 @Tool 对象注册成 ToolCallbackProvider Bean。
 * ReAct 执行器只消费 ToolCallbackProvider，不直接依赖具体工具类。
 */
@Configuration
@Slf4j
public class LocalToolCallbackProviderConfig {

    @Bean
    public ToolCallbackProvider localToolCallbackProvider(PlanDocxTools planDocxTools) {
        ToolCallbackProvider provider = MethodToolCallbackProvider.builder()
                .toolObjects(planDocxTools)
                .build();
        List<String> toolNames = Arrays.stream(provider.getToolCallbacks())
                .filter(callback -> callback != null && callback.getToolDefinition() != null)
                .map(callback -> callback.getToolDefinition().name())
                .toList();
        log.info("Local tools registered: {}", toolNames);
        return provider;
    }
}

