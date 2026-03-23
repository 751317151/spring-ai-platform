package com.huah.ai.platform.agent.memory;

import com.huah.ai.platform.agent.metrics.AiMetricsCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 会话记忆管理服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationMemoryService {

    private final StringRedisTemplate redisTemplate;
    private final ChatMemoryRepository chatMemoryRepository;
    private final AiMetricsCollector metricsCollector;

    private static final String USER_PROFILE_KEY = "ai:user:profile:";

    public ChatMemory getOrCreateMemory(String sessionId) {
        log.debug("获取会话记忆: sessionId={}", sessionId);
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(20)
                .build();
    }

    public void clearMemory(String sessionId) {
        chatMemoryRepository.deleteByConversationId(sessionId);
        log.debug("已清除会话记忆: sessionId={}", sessionId);
    }

    public List<Map<String, String>> getHistory(String sessionId) {
        List<Message> messages = chatMemoryRepository.findByConversationId(sessionId);
        return messages.stream()
                .filter(m -> m.getMessageType() == MessageType.USER || m.getMessageType() == MessageType.ASSISTANT)
                .map(m -> Map.of(
                        "role", m.getMessageType() == MessageType.USER ? "user" : "assistant",
                        "content", m.getText()
                ))
                .toList();
    }

    public void rollbackLastUserMessage(String sessionId) {
        List<Message> messages = chatMemoryRepository.findByConversationId(sessionId);
        if (messages.isEmpty()) {
            return;
        }

        Message last = messages.get(messages.size() - 1);
        if (last.getMessageType() == MessageType.USER) {
            List<Message> trimmed = new ArrayList<>(messages.subList(0, messages.size() - 1));
            chatMemoryRepository.saveAll(sessionId, trimmed);
            log.info("回滚孤立用户消息: sessionId={}, removedMessage={}",
                    sessionId, last.getText().length() > 50 ? last.getText().substring(0, 50) + "..." : last.getText());
        }
    }

    public void saveExchange(String sessionId, String userMessage, String aiResponse) {
        List<Message> existing = chatMemoryRepository.findByConversationId(sessionId);
        List<Message> updated = new ArrayList<>(existing);
        updated.add(new UserMessage(userMessage));
        updated.add(new AssistantMessage(aiResponse));
        chatMemoryRepository.saveAll(sessionId, updated);
        log.debug("保存对话记录: sessionId={}, totalMessages={}", sessionId, updated.size());
    }

    public void saveUserProfile(String userId, String key, String value) {
        String redisKey = USER_PROFILE_KEY + userId;
        try {
            redisTemplate.opsForHash().put(redisKey, key, value);
            redisTemplate.expire(redisKey, 30, TimeUnit.DAYS);
            log.debug("更新用户画像: userId={}, key={}", userId, key);
        } catch (Exception e) {
            metricsCollector.recordDependencyFailure("redis", "save-user-profile");
            log.warn("Redis 用户画像写入失败: userId={}, key={}, error={}", userId, key, e.getMessage());
        }
    }

    public String getUserProfile(String userId, String key) {
        String redisKey = USER_PROFILE_KEY + userId;
        try {
            Object val = redisTemplate.opsForHash().get(redisKey, key);
            return val != null ? val.toString() : null;
        } catch (Exception e) {
            metricsCollector.recordDependencyFailure("redis", "get-user-profile");
            log.warn("Redis 用户画像读取失败: userId={}, key={}, error={}", userId, key, e.getMessage());
            return null;
        }
    }

    public String buildPersonalizedContext(String userId) {
        String redisKey = USER_PROFILE_KEY + userId;
        try {
            Map<Object, Object> profile = redisTemplate.opsForHash().entries(redisKey);
            if (profile.isEmpty()) {
                return "";
            }

            StringBuilder sb = new StringBuilder("用户个人信息:\n");
            profile.forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append("\n"));
            return sb.toString();
        } catch (Exception e) {
            metricsCollector.recordDependencyFailure("redis", "build-personalized-context");
            log.warn("Redis 用户画像上下文构建失败: userId={}, error={}", userId, e.getMessage());
            return "";
        }
    }

    public List<Map<String, String>> listSessions(String prefix) {
        return chatMemoryRepository.findConversationIds().stream()
                .filter(id -> id.startsWith(prefix))
                .sorted(java.util.Comparator.reverseOrder())
                .map(id -> {
                    List<Message> msgs = chatMemoryRepository.findByConversationId(id);
                    String summary = msgs.stream()
                            .filter(m -> m.getMessageType() == MessageType.USER)
                            .map(Message::getText)
                            .findFirst()
                            .map(t -> t.length() > 30 ? t.substring(0, 30) + "..." : t)
                            .orElse("新对话");
                    return Map.of("sessionId", id, "summary", summary);
                })
                .toList();
    }

    public int getActiveSessionCount() {
        return chatMemoryRepository.findConversationIds().size();
    }
}
