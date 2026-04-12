package com.zhj.learn.aisuperhost.domain;

import java.util.Date;

public class ChatSummary {
    private String sessionId;

    private Long lastTurnId;

    private Date updatedAt;

    private String summaryContent;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Long getLastTurnId() {
        return lastTurnId;
    }

    public void setLastTurnId(Long lastTurnId) {
        this.lastTurnId = lastTurnId;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getSummaryContent() {
        return summaryContent;
    }

    public void setSummaryContent(String summaryContent) {
        this.summaryContent = summaryContent;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append(" [");
        sb.append("Hash = ").append(hashCode());
        sb.append(", sessionId=").append(sessionId);
        sb.append(", lastTurnId=").append(lastTurnId);
        sb.append(", updatedAt=").append(updatedAt);
        sb.append(", summaryContent=").append(summaryContent);
        sb.append("]");
        return sb.toString();
    }
}