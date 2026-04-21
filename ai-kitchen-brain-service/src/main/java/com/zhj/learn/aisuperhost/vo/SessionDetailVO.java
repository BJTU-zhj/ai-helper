package com.zhj.learn.aisuperhost.vo;

import java.util.ArrayList;
import java.util.List;

public class SessionDetailVO {

    private SessionVO session;

    private List<ChatMessageVO> messages = new ArrayList<>();

    public SessionVO getSession() {
        return session;
    }

    public void setSession(SessionVO session) {
        this.session = session;
    }

    public List<ChatMessageVO> getMessages() {
        return messages;
    }

    public void setMessages(List<ChatMessageVO> messages) {
        this.messages = messages == null ? new ArrayList<>() : messages;
    }
}

