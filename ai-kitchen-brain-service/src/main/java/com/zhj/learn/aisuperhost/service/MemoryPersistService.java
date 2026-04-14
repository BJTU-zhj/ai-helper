package com.zhj.learn.aisuperhost.service;

import com.zhj.learn.aicommon.util.SnowUtil;
import com.zhj.learn.aisuperhost.domain.ChatSummary;
import com.zhj.learn.aisuperhost.domain.WindowTurn;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 记忆策略服务：
 * 1) 会话记忆加载（redis 优先，mysql 回源）
 * 2) 会话记忆落库（事务A + redis 窗口刷新 + 摘要推进）
 */
@Service
public class MemoryPersistService {

    private final ChatSummaryService chatSummaryService;
    private final ChatHistoryService chatHistoryService;
    private final SessionService sessionService;
    private final RedisMemoryService redisMemoryService;
    private final ChatClient deepSeekChatClient;

    @Value("classpath:template/summary-generate.st")
    private org.springframework.core.io.Resource summaryTemplate;

    public MemoryPersistService(ChatSummaryService chatSummaryService,
                                ChatHistoryService chatHistoryService,
                                SessionService sessionService,
                                RedisMemoryService redisMemoryService,
                                @Qualifier("deepSeekChatClient") ChatClient deepSeekChatClient) {
        this.chatSummaryService = chatSummaryService;
        this.chatHistoryService = chatHistoryService;
        this.sessionService = sessionService;
        this.redisMemoryService = redisMemoryService;
        this.deepSeekChatClient = deepSeekChatClient;
    }

    /**
     * 加载摘要（redis 优先，mysql 回源）
     */
    public String loadSummary(String sessionId) {
        String summary = redisMemoryService.getSummary(sessionId);
        if (summary == null || summary.isBlank()) {
            ChatSummary chatSummary = chatSummaryService.getBySessionId(sessionId);
            summary = chatSummary == null ? "" : normalize(chatSummary.getSummaryContent());
            redisMemoryService.saveSummary(sessionId, summary);
        }
        return normalize(summary);
    }

    /**
     * 加载窗口（redis 优先，mysql 回源）
     */
    public List<WindowTurn> loadWindowTurns(String sessionId) {
        List<WindowTurn> turns = redisMemoryService.getWindowTurns(sessionId);
        if (turns == null || turns.isEmpty()) {
            turns = chatHistoryService.getWindowTurnsForRedis(sessionId, redisMemoryService.getWindowSize());
            if (turns != null && !turns.isEmpty()) {
                redisMemoryService.appendWindowTurn(sessionId, turns);
            }
        }
        return turns;
    }

    /**
     * 供 prompt 组装使用的窗口格式化
     */
    public String formatWindowTurns(List<WindowTurn> turns) {
        if (turns == null || turns.isEmpty()) {
            return "（暂无最近窗口对话）";
        }
        StringBuilder sb = new StringBuilder();
        for (WindowTurn t : turns) {
            if (t == null) {
                continue;
            }
            sb.append("第").append(t.getTurnNo() == null ? "-" : t.getTurnNo()).append("轮\n")
                    .append("用户: ").append(normalize(t.getUserContent())).append("\n")
                    .append("助手: ").append(normalize(t.getAssistantContent())).append("\n\n");
        }
        return sb.toString().trim();
    }

    /**
     * after 主流程：写历史、更新窗口、按挤出触发摘要。
     */
    public void persistAfterTurn(String sessionId, String userInput, String assistantOutput) {
        WindowTurn evictedWindowTurn = redisMemoryService.getAboutToEvictWindowTurn(sessionId);
        WindowTurn currentTurn = persistTurnTxA(sessionId, userInput, assistantOutput);
        redisMemoryService.appendWindowTurn(sessionId, currentTurn);

        if (evictedWindowTurn != null) {
            String oldSummary = loadSummary(sessionId);
            String newSummary = generateRollingSummary(oldSummary, List.of(evictedWindowTurn));
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
    }

    /**
     * MySQL 事务A：落本轮历史 + 推进 latest_history_id
     */
    @Transactional(rollbackFor = Exception.class)
    public WindowTurn persistTurnTxA(String sessionId, String userInput, String assistantOutput) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }

        String safeUserInput = normalize(userInput);
        String safeAssistantOutput = normalize(assistantOutput);

        sessionService.getOrCreate(sessionId, "新会话");
        ChatSummary summary = chatSummaryService.getBySessionId(sessionId);
        Objects.requireNonNull(summary, "chat summary must not be null");

        long nextTurnNo = chatHistoryService.nextTurnNo(sessionId);
        Date now = new Date();

        long userHistoryId = SnowUtil.getSnowflakeId();
        chatHistoryService.addHistory(userHistoryId, sessionId, nextTurnNo, "user", safeUserInput, now);

        long assistantHistoryId = SnowUtil.getSnowflakeId();
        chatHistoryService.addHistory(assistantHistoryId, sessionId, nextTurnNo, "assistant", safeAssistantOutput, now);

        chatSummaryService.touchAfterTurn(sessionId, assistantHistoryId, now);
        return new WindowTurn(nextTurnNo, safeUserInput, safeAssistantOutput, now.getTime());
    }

    /**
     * 摘要生成策略（deepseek）
     */
    public String generateRollingSummary(String oldSummary, List<WindowTurn> incrementalDialogue) {
        PromptTemplate promptTemplate = new PromptTemplate(summaryTemplate);
        Map<String, Object> vars = new HashMap<>();
        vars.put("old_summary", normalize(oldSummary));
        vars.put("incremental_dialogue", buildIncrementalDialogue(incrementalDialogue));

        String systemPrompt = promptTemplate.render(vars);
        String result = deepSeekChatClient.prompt()
                .system(systemPrompt)
                .call()
                .content();
        return normalize(result);
    }

    private String normalize(String text) {
        return text == null ? "" : text.trim();
    }

    private String buildIncrementalDialogue(List<WindowTurn> turns) {
        if (turns == null || turns.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (WindowTurn turn : turns) {
            if (turn == null) {
                continue;
            }
            sb.append("第").append(turn.getTurnNo() == null ? "-" : turn.getTurnNo()).append("轮\n");
            sb.append("用户: ").append(normalize(turn.getUserContent())).append("\n");
            sb.append("助手: ").append(normalize(turn.getAssistantContent())).append("\n\n");
        }
        return sb.toString().trim();
    }
}
