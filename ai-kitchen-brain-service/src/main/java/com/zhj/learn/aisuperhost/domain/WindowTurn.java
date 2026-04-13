package com.zhj.learn.aisuperhost.domain;

public class WindowTurn {
    private Long turnNo;
    private String userContent;
    private String assistantContent;
    private Long createdAtMillis;

    public WindowTurn() {
    }

    public WindowTurn(Long turnNo, String userContent, String assistantContent, Long createdAtMillis) {
        this.turnNo = turnNo;
        this.userContent = userContent;
        this.assistantContent = assistantContent;
        this.createdAtMillis = createdAtMillis;
    }

    public Long getTurnNo() {
        return turnNo;
    }

    public void setTurnNo(Long turnNo) {
        this.turnNo = turnNo;
    }

    public String getUserContent() {
        return userContent;
    }

    public void setUserContent(String userContent) {
        this.userContent = userContent;
    }

    public String getAssistantContent() {
        return assistantContent;
    }

    public void setAssistantContent(String assistantContent) {
        this.assistantContent = assistantContent;
    }

    public Long getCreatedAtMillis() {
        return createdAtMillis;
    }

    public void setCreatedAtMillis(Long createdAtMillis) {
        this.createdAtMillis = createdAtMillis;
    }
}