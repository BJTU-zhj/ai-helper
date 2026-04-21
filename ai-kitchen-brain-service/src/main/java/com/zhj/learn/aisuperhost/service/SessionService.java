package com.zhj.learn.aisuperhost.service;

import com.zhj.learn.aicommon.util.SnowUtil;
import com.zhj.learn.aisuperhost.domain.Session;
import com.zhj.learn.aisuperhost.domain.SessionExample;
import com.zhj.learn.aisuperhost.mapper.SessionMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Service
public class SessionService {

    @Resource
    private SessionMapper sessionMapper;

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private ChatSummaryService chatSummaryService;

    @Resource
    private RedisMemoryService redisMemoryService;

    /**
     * 后端生成 sessionId 创建会话。
     */
    @Transactional(rollbackFor = Exception.class)
    public Session createSession(String title) {
        return createSession(SnowUtil.getSnowflakeIdStr(), title);
    }

    /**
     * 前端提供 sessionId 创建会话。
     */
    @Transactional(rollbackFor = Exception.class)
    public Session createSession(String sessionId, String title) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        Date now = new Date();
        Session session = new Session();
        session.setId(sessionId.trim());
        session.setTitle(normalizeTitle(title));
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        sessionMapper.insertSelective(session);
        return session;
    }

    /**
     * 按会话ID查询。
     */
    public Session getById(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        return sessionMapper.selectByPrimaryKey(sessionId.trim());
    }

    /**
     * 查询全部会话，按最近更新时间倒序返回。
     */
    public List<Session> listSessions() {
        SessionExample example = new SessionExample();
        example.setOrderByClause("updated_at desc, created_at desc");
        return sessionMapper.selectByExample(example);
    }

    /**
     * 若会话不存在则创建；存在则返回已有会话。
     */
    @Transactional(rollbackFor = Exception.class)
    public Session getOrCreate(String sessionId, String defaultTitle) {
        Session existed = getById(sessionId);
        if (existed != null) {
            return existed;
        }
        return createSession(sessionId, defaultTitle);
    }

    /**
     * 更新会话标题。
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean updateTitle(String sessionId, String newTitle) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        Session update = new Session();
        update.setId(sessionId.trim());
        update.setTitle(normalizeTitle(newTitle));
        update.setUpdatedAt(new Date());
        return sessionMapper.updateByPrimaryKeySelective(update) > 0;
    }

    /**
     * 删除会话及其数据库历史、摘要和 Redis 短期记忆。
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        String normalizedSessionId = sessionId.trim();
        chatHistoryService.deleteBySessionId(normalizedSessionId);
        chatSummaryService.deleteBySessionId(normalizedSessionId);
        redisMemoryService.clearSessionMemory(normalizedSessionId);
        return sessionMapper.deleteByPrimaryKey(normalizedSessionId) > 0;
    }

    /**
     * 刷新会话更新时间。
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean touch(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        Session update = new Session();
        update.setId(sessionId.trim());
        update.setUpdatedAt(new Date());
        return sessionMapper.updateByPrimaryKeySelective(update) > 0;
    }

    private String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            return "新会话";
        }
        return title.trim();
    }
}
