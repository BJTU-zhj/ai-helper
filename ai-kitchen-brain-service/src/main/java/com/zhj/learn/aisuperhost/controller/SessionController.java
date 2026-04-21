package com.zhj.learn.aisuperhost.controller;

import com.zhj.learn.aicommon.VO.CommonResp;
import com.zhj.learn.aisuperhost.vo.ChatMessageVO;
import com.zhj.learn.aisuperhost.dto.CreateSessionDTO;
import com.zhj.learn.aisuperhost.vo.SessionDetailVO;
import com.zhj.learn.aisuperhost.vo.SessionVO;
import com.zhj.learn.aisuperhost.dto.UpdateSessionTitleDTO;
import com.zhj.learn.aisuperhost.service.AiService;
import com.zhj.learn.aisuperhost.service.ChatHistoryService;
import com.zhj.learn.aisuperhost.service.SessionService;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/brain")
public class SessionController {

    @Resource
    private AiService aiService;

    @Resource
    private SessionService sessionService;

    @Resource
    private ChatHistoryService chatHistoryService;

    @PostMapping("/sessions")
    public CommonResp<SessionVO> createSession(@RequestBody(required = false) CreateSessionDTO req) {
        String title = req == null ? null : req.getTitle();
        return new CommonResp<>(SessionVO.from(sessionService.createSession(title)));
    }

    @GetMapping("/sessions")
    public CommonResp<List<SessionVO>> listSessions() {
        List<SessionVO> sessions = sessionService.listSessions().stream()
                .map(SessionVO::from)
                .collect(Collectors.toList());
        return new CommonResp<>(sessions);
    }

    @GetMapping("/sessions/{sessionId}")
    public CommonResp<SessionDetailVO> getSessionDetail(@PathVariable String sessionId) {
        SessionDetailVO detail = new SessionDetailVO();
        detail.setSession(SessionVO.from(sessionService.getById(sessionId)));
        detail.setMessages(chatHistoryService.listBySessionId(sessionId).stream()
                .map(ChatMessageVO::from)
                .collect(Collectors.toList()));
        return new CommonResp<>(detail);
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public CommonResp<List<ChatMessageVO>> listSessionMessages(@PathVariable String sessionId) {
        List<ChatMessageVO> messages = chatHistoryService.listBySessionId(sessionId).stream()
                .map(ChatMessageVO::from)
                .collect(Collectors.toList());
        return new CommonResp<>(messages);
    }

    @PutMapping("/sessions/{sessionId}/title")
    public CommonResp<SessionVO> updateSessionTitle(@PathVariable String sessionId,
                                                    @RequestBody UpdateSessionTitleDTO req) {
        sessionService.updateTitle(sessionId, req == null ? null : req.getTitle());
        return new CommonResp<>(SessionVO.from(sessionService.getById(sessionId)));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public CommonResp<Boolean> deleteSession(@PathVariable String sessionId) {
        return new CommonResp<>(sessionService.deleteSession(sessionId));
    }

    @GetMapping("/chat/{sessionId}/{message}")
    public CommonResp<String> chat(@PathVariable String sessionId, @PathVariable String message){
        return new CommonResp<>(aiService.chat(sessionId, message));
    }

    @GetMapping(value = "/chat/stream/{sessionId}/{message}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@PathVariable String sessionId, @PathVariable String message) {
        return aiService.streamChat(sessionId, message);
    }

}
