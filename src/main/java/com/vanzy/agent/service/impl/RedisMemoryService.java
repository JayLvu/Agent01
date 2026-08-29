package com.vanzy.agent.service.impl;

import com.vanzy.agent.config.AgentProperties;
import com.vanzy.agent.model.ChatMessage;
import com.vanzy.agent.service.MemoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 基于 Redis 的对话记忆实现
 *
 * 存储结构: Redis List, key = "agent:memory:{sessionId}"
 * 优势: 多实例共享、自动 TTL 过期、支持持久化
 *
 * @author VanzyLiu
 */
@Slf4j
@Service
public class RedisMemoryService implements MemoryService {

    private static final String KEY_PREFIX = "agent:memory:";
    private static final String SUMMARY_PREFIX = "agent:memory:summary:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final AgentProperties properties;

    public RedisMemoryService(RedisTemplate<String, Object> redisTemplate, AgentProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    public void saveMessage(String sessionId, ChatMessage message) {
        String key = KEY_PREFIX + sessionId;
        redisTemplate.opsForList().rightPush(key, message);
        // 只保留最近 N 条消息
        int maxHistory = properties.getMemory().getMaxHistory();
        Long size = redisTemplate.opsForList().size(key);
        if (size != null && size > maxHistory) {
            redisTemplate.opsForList().trim(key, size - maxHistory, -1);
        }
        // 刷新 TTL
        redisTemplate.expire(key, Duration.ofHours(properties.getMemory().getTtlHours()));
        log.debug("保存消息到会话: {}, 角色: {}, 当前消息数: {}", sessionId, message.getRole(), size);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ChatMessage> getHistory(String sessionId) {
        String key = KEY_PREFIX + sessionId;
        List<Object> raw = redisTemplate.opsForList().range(key, 0, -1);
        if (CollectionUtils.isEmpty(raw)) {
            return Collections.emptyList();
        }
        List<ChatMessage> history = new ArrayList<>(raw.size());
        for (Object item : raw) {
            if (item instanceof ChatMessage cm) {
                history.add(cm);
            } else {
                // Jackson 反序列化的 LinkedHashMap 转 ChatMessage
                log.warn("历史消息类型不匹配: {}", item.getClass());
            }
        }
        return history;
    }

    @Override
    public void clearHistory(String sessionId) {
        redisTemplate.delete(KEY_PREFIX + sessionId);
        redisTemplate.delete(SUMMARY_PREFIX + sessionId);
        log.info("已清空会话历史: {}", sessionId);
    }

    @Override
    public void saveSummary(String sessionId, String summary) {
        redisTemplate.opsForValue().set(SUMMARY_PREFIX + sessionId, summary,
                Duration.ofHours(properties.getMemory().getTtlHours()));
    }

    @Override
    public String getSummary(String sessionId) {
        Object v = redisTemplate.opsForValue().get(SUMMARY_PREFIX + sessionId);
        return v == null ? null : String.valueOf(v);
    }

    @Override
    public void trimHistory(String sessionId, int keepRecent) {
        if (keepRecent <= 0) {
            clearHistory(sessionId);
            return;
        }
        redisTemplate.opsForList().trim(KEY_PREFIX + sessionId, -keepRecent, -1);
        redisTemplate.expire(KEY_PREFIX + sessionId, Duration.ofHours(properties.getMemory().getTtlHours()));
    }

    @Override
    public String createSession() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
