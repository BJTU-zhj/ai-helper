package com.zhj.learn.aisuperhost.ai.chatclient;

import com.zhj.learn.aisuperhost.ai.advisor.MemoryLoadAdvisor;
import com.zhj.learn.aisuperhost.ai.react.advisor.PlannerStepContextAdvisor;
import com.zhj.learn.aisuperhost.ai.react.advisor.ReactMemoryPersistAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ReAct Planner 专用 ChatClient。
 *
 * <p>注意：
 * 1. 不挂载任何 advisors（避免污染会话记忆与 RAG 链路）。
 * 2. 不挂载任何 tools / mcp callbacks（避免 Planner 阶段自动触发工具调用）。
 * 3. 仅用于输出结构化计划 JSON（ReactPlan）。
 */
@Configuration
public class MyPlannerClient {

    @Bean
    public ChatClient qwenPlannerChatClient(@Qualifier("qwenChatModel") ChatModel qwenChatModel,
                                            MemoryLoadAdvisor memoryLoadAdvisor,
                                            PlannerStepContextAdvisor plannerStepContextAdvisor,
                                            ReactMemoryPersistAdvisor reactMemoryPersistAdvisor) {
        return ChatClient.builder(qwenChatModel)
                .defaultAdvisors(memoryLoadAdvisor, plannerStepContextAdvisor, reactMemoryPersistAdvisor)
                .build();
    }
}
