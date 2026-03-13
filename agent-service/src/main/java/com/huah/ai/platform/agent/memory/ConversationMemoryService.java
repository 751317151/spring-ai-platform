package com.huah.ai.platform.agent.memory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 会话记忆管理服务
 * - 短期记忆：基于 InMemoryChatMemory（滑动窗口）
 * - 长期记忆：Redis 持久化用户画像
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationMemoryService {

    private final StringRedisTemplate redisTemplate;

    /** 短期会话记忆缓存 */
    private final Map<String, ChatMemory> sessionMemories = new ConcurrentHashMap<>();

    private static final String USER_PROFILE_KEY = "ai:user:profile:";
    private static final int SESSION_TTL_HOURS = 24;

    /**
     * 获取或创建会话记忆（短期）
     */
    public ChatMemory getOrCreateMemory(String sessionId) {
        return sessionMemories.computeIfAbsent(sessionId, id -> {
            log.debug("创建新会话记忆: sessionId={}", id);
            return MessageWindowChatMemory.builder()
                    .chatMemoryRepository(new InMemoryChatMemoryRepository())
                    .maxMessages(20)
                    .build();
        });
    }

    /**
     * 清除会话记忆
     */
    public void clearMemory(String sessionId) {
        sessionMemories.remove(sessionId);
        log.debug("已清除会话记忆: sessionId={}", sessionId);
    }

    /**
     * 保存用户长期画像（偏好、背景信息等）
     */
    public void saveUserProfile(String userId, String key, String value) {
        String redisKey = USER_PROFILE_KEY + userId;
        redisTemplate.opsForHash().put(redisKey, key, value);
        redisTemplate.expire(redisKey, 30, TimeUnit.DAYS);
        log.debug("更新用户画像: userId={}, key={}", userId, key);
    }

    /**
     * 获取用户画像字段
     */
    public String getUserProfile(String userId, String key) {
        String redisKey = USER_PROFILE_KEY + userId;
        Object val = redisTemplate.opsForHash().get(redisKey, key);
        return val != null ? val.toString() : null;
    }

    /**
     * 获取完整用户画像，用于构建个性化 System Prompt
     */
    public String buildPersonalizedContext(String userId) {
        String redisKey = USER_PROFILE_KEY + userId;
        Map<Object, Object> profile = redisTemplate.opsForHash().entries(redisKey);
        if (profile.isEmpty()) return "";

        StringBuilder sb = new StringBuilder("用户个人信息：\n");
        profile.forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append("\n"));
        return sb.toString();
    }

    /**
     * 统计当前活跃会话数
     */
    public int getActiveSessionCount() {
        return sessionMemories.size();
    }
}
