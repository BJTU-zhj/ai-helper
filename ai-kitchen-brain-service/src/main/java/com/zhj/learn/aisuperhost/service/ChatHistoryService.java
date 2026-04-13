package com.zhj.learn.aisuperhost.service;

import com.zhj.learn.aisuperhost.domain.ChatHistory;
import com.zhj.learn.aisuperhost.domain.ChatHistoryExample;
import com.zhj.learn.aisuperhost.domain.WindowTurn;
import com.zhj.learn.aisuperhost.mapper.ChatHistoryMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
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

    // 获取下一轮轮次
    public long nextTurnNo(String sessionId) {
        return getMaxTurnNo(sessionId) + 1;
    }

    // 新增一条历史记录
    public ChatHistory addHistory(long id, String sessionId, long turnNo, String role, String content, Date createdAt) {
        ChatHistory history = new ChatHistory();
        history.setId(id);
        history.setSessionId(sessionId);
        history.setTurnNo(turnNo);
        history.setRole(role);
        history.setContent(content == null ? "" : content);
        history.setCreatedAt(createdAt == null ? new Date() : createdAt);
        chatHistoryMapper.insertSelective(history);
        return history;
    }

    // 按会话+轮次查询 assistant 那条历史ID（用于推进摘要游标）
    public Long getAssistantHistoryId(String sessionId, Long turnNo) {
        if (sessionId == null || sessionId.isBlank() || turnNo == null) {
            return null;
        }
        ChatHistoryExample example = new ChatHistoryExample();
        example.createCriteria()
                .andSessionIdEqualTo(sessionId)
                .andTurnNoEqualTo(turnNo)
                .andRoleEqualTo("assistant");
        example.setOrderByClause("created_at desc");
        List<ChatHistory> histories = chatHistoryMapper.selectByExample(example);
        if (histories == null || histories.isEmpty()) {
            return null;
        }
        return histories.get(0).getId();
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
