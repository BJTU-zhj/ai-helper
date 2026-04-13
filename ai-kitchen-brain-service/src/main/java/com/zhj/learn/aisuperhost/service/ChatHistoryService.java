package com.zhj.learn.aisuperhost.service;

import com.zhj.learn.aisuperhost.domain.ChatHistory;
import com.zhj.learn.aisuperhost.domain.ChatHistoryExample;
import com.zhj.learn.aisuperhost.domain.WindowTurn;
import com.zhj.learn.aisuperhost.mapper.ChatHistoryMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ChatHistoryService {

    @Resource
    private ChatHistoryMapper chatHistoryMapper;

    /**
     * 根据会话ID + 窗口大小，查询最近N轮（按轮次）并组装成 WindowTurn 列表，
     * 供 Redis window 回填使用。
     */
    public List<WindowTurn> getWindowTurnsForRedis(String sessionId, int windowSize) {
        if (sessionId == null || sessionId.isBlank() || windowSize <= 0) {
            return Collections.emptyList();
        }

        ChatHistoryExample example = new ChatHistoryExample();
        example.createCriteria().andSessionIdEqualTo(sessionId);
        example.setOrderByClause("turn_no desc, id desc");

        List<ChatHistory> histories = chatHistoryMapper.selectByExampleWithBLOBs(example);
        if (histories == null || histories.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, WindowTurn> turnMap = new LinkedHashMap<>();
        for (ChatHistory history : histories) {
            if (history.getTurnNo() == null) {
                continue;
            }

            WindowTurn turn = turnMap.computeIfAbsent(
                    history.getTurnNo(),
                    turnNo -> new WindowTurn(turnNo, "", "", history.getCreatedAt() == null ? null : history.getCreatedAt().getTime())
            );

            if (isUser(history.getRole())) {
                turn.setUserContent(history.getContent());
            } else if (isAssistant(history.getRole())) {
                turn.setAssistantContent(history.getContent());
            }

            if (history.getCreatedAt() != null) {
                turn.setCreatedAtMillis(history.getCreatedAt().getTime());
            }

            if (turnMap.size() >= windowSize) {
                boolean allComplete = true;
                for (WindowTurn wt : turnMap.values()) {
                    if (isBlank(wt.getUserContent()) || isBlank(wt.getAssistantContent())) {
                        allComplete = false;
                        break;
                    }
                }
                if (allComplete) {
                    break;
                }
            }
        }

        List<WindowTurn> turns = new ArrayList<>(turnMap.values());
        if (turns.size() > windowSize) {
            turns = turns.subList(0, windowSize);
        }
        Collections.reverse(turns);
        return turns;
    }

    /**
     * 获取会话当前最大轮次。没有历史时返回0。
     */
    public long getMaxTurnNo(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return 0L;
        }
        ChatHistoryExample example = new ChatHistoryExample();
        example.createCriteria().andSessionIdEqualTo(sessionId);
        example.setOrderByClause("turn_no desc");
        List<ChatHistory> histories = chatHistoryMapper.selectByExample(example);
        if (histories == null || histories.isEmpty() || histories.get(0).getTurnNo() == null) {
            return 0L;
        }
        return histories.get(0).getTurnNo();
    }

    private boolean isUser(String role) {
        return "user".equalsIgnoreCase(role);
    }

    private boolean isAssistant(String role) {
        return "assistant".equalsIgnoreCase(role);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
