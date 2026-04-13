package com.zhj.learn.aisuperhost.service;


import cn.hutool.core.date.DateTime;
import com.zhj.learn.aisuperhost.domain.ChatSummary;
import com.zhj.learn.aisuperhost.domain.ChatSummaryExample;
import com.zhj.learn.aisuperhost.mapper.ChatSummaryMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class ChatSummaryService {

    @Resource
    private ChatSummaryMapper chatSummaryMapper;

    //根据会话id查询会话摘要
    public ChatSummary getBySessionId(String sessionId){

        ChatSummaryExample example = new ChatSummaryExample();

        //先看看有没有
        ChatSummary chatSummary = chatSummaryMapper.selectByPrimaryKey(sessionId);
        //初始没有
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
        else {
            return chatSummary;
        }
    }

    //会话
    public void touchAfterTurn(String sessionId, long lastTurnId){


    }

    //
}
