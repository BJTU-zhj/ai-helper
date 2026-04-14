package com.zhj.learn.aisuperhost.ai.advisor;

import com.zhj.learn.aisuperhost.domain.WindowTurn;
import com.zhj.learn.aisuperhost.service.AiService;
import com.zhj.learn.aisuperhost.service.ChatHistoryService;
import com.zhj.learn.aisuperhost.service.ChatSummaryService;
import com.zhj.learn.aisuperhost.service.MemoryPersistService;
import com.zhj.learn.aisuperhost.service.RedisMemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseChatMemoryAdvisor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Component
public class MemoryPersistAdvisor implements BaseChatMemoryAdvisor {

    private static final Logger LOG = LoggerFactory.getLogger(MemoryPersistAdvisor.class);

    @Autowired
    private RedisMemoryService redisMemoryService;

    @Autowired
    private ChatSummaryService chatSummaryService;

    @Autowired
    private ChatHistoryService chatHistoryService;

    @Autowired
    private MemoryPersistService memoryPersistService;

    @Autowired
    private AiService aiService;

    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        return chatClientRequest;
    }

    // 更新 redis/mysql；失败不影响主回答返回
    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        try {
            Map<String, Object> chatContext = chatClientResponse.context();
            String sessionId = getConversationId(chatContext, "default");

            // 找到本轮用户输入和助手输出
            String userInput = (String) chatContext.getOrDefault("raw_user_input", "");
            String assistantOutput = extractAssistantOutput(chatClientResponse);

            // 先窥探即将淘汰轮次（append 前）
            WindowTurn evictedWindowTurn = redisMemoryService.getAboutToEvictWindowTurn(sessionId);

            // MySQL 事务A：落本轮历史 + 推进 latest_history_id
            WindowTurn currentTurn = memoryPersistService.persistTurnTxA(sessionId, userInput, assistantOutput);

            // 刷新 Redis 窗口
            redisMemoryService.appendWindowTurn(sessionId, currentTurn);

            // 若发生挤出，触发一次摘要更新
            if (evictedWindowTurn != null) {
                String oldSummary = redisMemoryService.getSummary(sessionId);
                if (oldSummary == null || oldSummary.isBlank()) {
                    oldSummary = chatSummaryService.getBySessionId(sessionId).getSummaryContent();
                }

                String newSummary = aiService.generateRollingSummary(oldSummary, List.of(evictedWindowTurn));
                Long lastSummarizedHistoryId = chatHistoryService.getAssistantHistoryId(sessionId, evictedWindowTurn.getTurnNo());
                if (lastSummarizedHistoryId != null) {
                    chatSummaryService.updateSummaryAfterGenerated(
                            sessionId,
                            newSummary,
                            lastSummarizedHistoryId,
                            new Date()
                    );
                }
                redisMemoryService.saveSummary(sessionId, newSummary);
            }
        } catch (Exception e) {
            LOG.error("memory persist after hook failed", e);
        }
        return chatClientResponse;
    }

    @Override
    public int getOrder() {
        return 200;
    }

    private String extractAssistantOutput(ChatClientResponse response) {
        try {
            String text = response.chatResponse().getResult().getOutput().getText();
            return text == null ? "" : text;
        } catch (Exception e) {
            return "";
        }
    }
}
