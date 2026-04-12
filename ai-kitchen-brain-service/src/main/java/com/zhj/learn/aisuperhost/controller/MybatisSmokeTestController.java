package com.zhj.learn.aisuperhost.controller;

import com.zhj.learn.aisuperhost.domain.Session;
import com.zhj.learn.aisuperhost.domain.SessionExample;
import com.zhj.learn.aisuperhost.mapper.SessionMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test/mybatis")
public class MybatisSmokeTestController {

    private final SessionMapper sessionMapper;

    public MybatisSmokeTestController(SessionMapper sessionMapper) {
        this.sessionMapper = sessionMapper;
    }

    @GetMapping("/session")
    public Map<String, Object> sessionList() {
        SessionExample example = new SessionExample();
        example.setOrderByClause("created_at desc");

        List<Session> sessions = sessionMapper.selectByExample(example);

        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        result.put("count", sessions.size());
        result.put("first", sessions.isEmpty() ? null : sessions.get(0));
        return result;
    }

    @GetMapping("/session/{id}")
    public Map<String, Object> sessionById(@PathVariable String id) {
        Session session = sessionMapper.selectByPrimaryKey(id);

        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        result.put("found", session != null);
        result.put("data", session);
        return result;
    }
}
