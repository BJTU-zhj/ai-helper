package com.zhj.learn.aihelper.dto;

public class UtilityChatResponse {

    private String sessionId;

    private String answer;

    public UtilityChatResponse() {
    }

    public UtilityChatResponse(String sessionId, String answer) {
        this.sessionId = sessionId;
        this.answer = answer;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }
}
