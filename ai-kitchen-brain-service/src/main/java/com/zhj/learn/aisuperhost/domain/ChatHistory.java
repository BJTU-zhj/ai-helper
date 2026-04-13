package com.zhj.learn.aisuperhost.domain;

import java.util.Date;

public class ChatHistory {
    private Long id;

    private String sessionId;

    private Long turnNo;

    private String role;

    private Date createdAt;

    private String content;

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

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append(" [");
        sb.append("Hash = ").append(hashCode());
        sb.append(", id=").append(id);
        sb.append(", sessionId=").append(sessionId);
        sb.append(", turnNo=").append(turnNo);
        sb.append(", role=").append(role);
        sb.append(", createdAt=").append(createdAt);
        sb.append(", content=").append(content);
        sb.append("]");
        return sb.toString();
    }
}