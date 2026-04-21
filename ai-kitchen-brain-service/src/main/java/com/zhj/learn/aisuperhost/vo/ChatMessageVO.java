package com.zhj.learn.aisuperhost.vo;

import com.zhj.learn.aisuperhost.domain.ChatHistory;

import java.util.Date;

public class ChatMessageVO {

    private Long id;

    private String sessionId;

    private Long turnNo;

    private String role;

    private String content;

    private Date createdAt;

    public static ChatMessageVO from(ChatHistory history) {
        if (history == null) {
            return null;
        }
        ChatMessageVO vo = new ChatMessageVO();
        vo.setId(history.getId());
        vo.setSessionId(history.getSessionId());
        vo.setTurnNo(history.getTurnNo());
        vo.setRole(history.getRole());
        vo.setContent(history.getContent());
        vo.setCreatedAt(history.getCreatedAt());
        return vo;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Long getTurnNo() {
        return turnNo;
    }

    public void setTurnNo(Long turnNo) {
        this.turnNo = turnNo;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
}

