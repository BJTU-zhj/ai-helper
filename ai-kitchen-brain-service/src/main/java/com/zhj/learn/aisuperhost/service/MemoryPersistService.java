package com.zhj.learn.aisuperhost.service;

import com.zhj.learn.aicommon.util.SnowUtil;
import com.zhj.learn.aisuperhost.domain.ChatHistory;
import com.zhj.learn.aisuperhost.domain.ChatSummary;
import com.zhj.learn.aisuperhost.domain.WindowTurn;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Objects;

@Service
public class MemoryPersistService {

    @Resource
    private ChatSummaryService chatSummaryService;

    @Resource
    private ChatHistoryService chatHistoryService;

    /**
     * MySQL 事务A：
     * 1) 写入本轮 user / assistant 两条历史
     * 2) 推进 chat_summary.latest_history_id
     *
     * @return 本轮窗口对象（可直接用于 Redis append）
     */
    @Transactional(rollbackFor = Exception.class)
    public WindowTurn persistTurnTxA(String sessionId, String userInput, String assistantOutput) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }

        String safeUserInput = normalize(userInput);
        String safeAssistantOutput = normalize(assistantOutput);

        // 保证 chat_summary 行存在（没有就初始化）
        ChatSummary summary = chatSummaryService.getBySessionId(sessionId);
        Objects.requireNonNull(summary, "chat summary must not be null");

        long nextTurnNo = chatHistoryService.nextTurnNo(sessionId);
        Date now = new Date();

        long userHistoryId = SnowUtil.getSnowflakeId();
        ChatHistory userHistory = chatHistoryService.addHistory(
                userHistoryId, sessionId, nextTurnNo, "user", safeUserInput, now
        );

        long assistantHistoryId = SnowUtil.getSnowflakeId();
        ChatHistory assistantHistory = chatHistoryService.addHistory(
                assistantHistoryId, sessionId, nextTurnNo, "assistant", safeAssistantOutput, now
        );

        chatSummaryService.touchAfterTurn(sessionId, assistantHistoryId, now);

        return new WindowTurn(nextTurnNo, safeUserInput, safeAssistantOutput, now.getTime());
    }

    private String normalize(String text) {
        return text == null ? "" : text;
    }

}
