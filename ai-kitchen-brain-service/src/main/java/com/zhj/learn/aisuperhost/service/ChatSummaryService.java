package com.zhj.learn.aisuperhost.service;


import cn.hutool.core.date.DateTime;
import com.zhj.learn.aisuperhost.domain.ChatSummary;
import com.zhj.learn.aisuperhost.mapper.ChatSummaryMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * 会话摘要服务
 */

@Service
public class ChatSummaryService {

    @Resource
    private ChatSummaryMapper chatSummaryMapper;

    // 根据会话id查询会话摘要（不存在则初始化）
    public ChatSummary getBySessionId(String sessionId){
        ChatSummary chatSummary = chatSummaryMapper.selectByPrimaryKey(sessionId);
        if(chatSummary==null){
            Date now = DateTime.now();

            ChatSummary newChatSummary = new ChatSummary();
            newChatSummary.setLatestHistoryId(0L);
            newChatSummary.setLastSummarizedHistoryId(0L);
            newChatSummary.setSessionId(sessionId);
            newChatSummary.setUpdatedAt(now);
            newChatSummary.setSummaryContent("");

            chatSummaryMapper.insertSelective(newChatSummary);
            return chatSummaryMapper.selectByPrimaryKey(sessionId);
        }
        return chatSummary;
    }

    // 推进会话游标：每轮写历史后调用
    public void touchAfterTurn(String sessionId, long latestHistoryId, Date updatedAt){
        ChatSummary summary = getBySessionId(sessionId);
        if (summary == null) {
            throw new IllegalStateException("chat summary init failed for sessionId=" + sessionId);
        }

        ChatSummary update = new ChatSummary();
        update.setSessionId(sessionId);
        update.setLatestHistoryId(latestHistoryId);
        update.setUpdatedAt(updatedAt == null ? new Date() : updatedAt);

        chatSummaryMapper.updateByPrimaryKeySelective(update);
    }

    // 更新摘要内容与摘要游标：摘要成功后调用
    public void updateSummaryAfterGenerated(String sessionId, String summaryContent, long lastSummarizedHistoryId, Date updatedAt) {
        ChatSummary summary = getBySessionId(sessionId);
        if (summary == null) {
            throw new IllegalStateException("chat summary init failed for sessionId=" + sessionId);
        }

        ChatSummary update = new ChatSummary();
        update.setSessionId(sessionId);
        update.setSummaryContent(summaryContent == null ? "" : summaryContent);
        update.setLastSummarizedHistoryId(lastSummarizedHistoryId);
        update.setUpdatedAt(updatedAt == null ? new Date() : updatedAt);

        chatSummaryMapper.updateByPrimaryKeySelective(update);
    }
}
