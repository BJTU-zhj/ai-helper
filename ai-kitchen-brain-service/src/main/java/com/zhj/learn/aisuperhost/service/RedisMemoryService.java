package com.zhj.learn.aisuperhost.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhj.learn.aisuperhost.domain.WindowTurn;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Redis 存储服务
 */

@Service
@Getter
public class RedisMemoryService {

    private static final Logger LOG = LoggerFactory.getLogger(RedisMemoryService.class);
    private static final String SUMMARY_KEY_PREFIX = "brain:summary:";
    private static final String WINDOW_KEY_PREFIX = "brain:window:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${memory.redis.window-size:10}")
    private int windowSize;

    @Value("${memory.redis.summary-ttl-hours:72}")
    private long summaryTtlHours;

    @Value("${memory.redis.window-ttl-hours:72}")
    private long windowTtlHours;

    public RedisMemoryService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    private String summaryKey(String sessionId) {
        return SUMMARY_KEY_PREFIX + sessionId;
    }

    private String windowKey(String sessionId) {
        return WINDOW_KEY_PREFIX + sessionId;
    }


    // 获取会话摘要String类型
    public String getSummary(String sessionId) {

        return redisTemplate.opsForValue().get(summaryKey(sessionId)
        );
    }

    // 保存会话摘要String类型
    public void saveSummary(String sessionId, String summaryContent) {
        String content = summaryContent == null ? "" : summaryContent;
        redisTemplate.opsForValue().set(summaryKey(sessionId), content, Duration.ofHours(summaryTtlHours));
    }

    // 获取会话窗口，list类型
    public List<WindowTurn> getWindowTurns(String sessionId) {
        List<String> raw = redisTemplate.opsForList().range(windowKey(sessionId), 0, -1);
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }

        List<WindowTurn> turns = new ArrayList<>(raw.size());
        for (String item : raw) {
            WindowTurn parsed = parseWindowTurn(sessionId, item);
            if (parsed != null) {
                turns.add(parsed);
            }
        }
        return turns;
    }

    // 获取“下一次追加时即将被挤出”的最老一轮；窗口未满则返回 null
    public WindowTurn getAboutToEvictWindowTurn(String sessionId) {
        String key = windowKey(sessionId);
        Long size = redisTemplate.opsForList().size(key);
        if (size == null || size < windowSize) {
            return null;
        }
        String headPayload = redisTemplate.opsForList().index(key, 0);
        return parseWindowTurn(sessionId, headPayload);
    }

    // 追加会话，list类型
    public void appendWindowTurn(String sessionId, WindowTurn turn) {
        Objects.requireNonNull(turn, "turn must not be null");
        appendWindowTurn(sessionId, Collections.singletonList(turn));
    }

    // 批量追加会话窗口，适用于 redis 失效后首次从数据库回填多轮历史
    public void appendWindowTurn(String sessionId, List<WindowTurn> turn) {
        Objects.requireNonNull(turn, "turn must not be null");
        if (turn.isEmpty()) {
            return;
        }

        String key = windowKey(sessionId);
        try {
            List<String> payloads = new ArrayList<>(turn.size());
            for (WindowTurn item : turn) {
                if (item == null) {
                    continue;
                }
                payloads.add(objectMapper.writeValueAsString(item));
            }
            if (payloads.isEmpty()) {
                return;
            }

            // 批量追加会话
            redisTemplate.opsForList().rightPushAll(key, payloads);
            // 假设 windowSize 是 3。-3 表示倒数第 3 个元素，-1 表示最后一个元素。
            // 这行代码命令 Redis：“请只保留从倒数第 3 个到倒数第 1 个元素，前面的全部删掉！”
            redisTemplate.opsForList().trim(key, -windowSize, -1);
            // 设置会话过期时间
            redisTemplate.expire(key, Duration.ofHours(windowTtlHours));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize window turn", e);
        }
    }

    // 清空会话
    public void clearSessionMemory(String sessionId) {
        redisTemplate.delete(summaryKey(sessionId));
        redisTemplate.delete(windowKey(sessionId));
    }

    private WindowTurn parseWindowTurn(String sessionId, String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(payload, WindowTurn.class);
        } catch (JsonProcessingException e) {
            LOG.warn("Failed to parse redis window item. sessionId={}, payload={}", sessionId, payload, e);
            return null;
        }
    }


}
