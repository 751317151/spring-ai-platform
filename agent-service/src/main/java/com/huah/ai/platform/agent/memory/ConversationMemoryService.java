package com.huah.ai.platform.agent.memory;

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
 * 会话记忆管理服务
 * - 短期记忆：基于 JDBC 持久化（PostgreSQL）+ 滑动窗口
 * - 长期记忆：Redis 持久化用户画像
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationMemoryService {

    private final StringRedisTemplate redisTemplate;
    private final ChatMemoryRepository chatMemoryRepository;

    private static final String USER_PROFILE_KEY = "ai:user:profile:";

    /**
     * 获取或创建会话记忆（JDBC 持久化）
     */
    public ChatMemory getOrCreateMemory(String sessionId) {
        log.debug("获取会话记忆: sessionId={}", sessionId);
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(20)
                .build();
    }

    /**
     * 清除会话记忆
     */
    public void clearMemory(String sessionId) {
        chatMemoryRepository.deleteByConversationId(sessionId);
        log.debug("已清除会话记忆: sessionId={}", sessionId);
    }

    /**
     * 查询会话历史消息（仅返回 USER 和 ASSISTANT 类型）
     */
    public List<Map<String, String>> getHistory(String sessionId) {
        List<Message> messages = chatMemoryRepository.findByConversationId(sessionId);
        return messages.stream()
                .filter(m -> m.getMessageType() == MessageType.USER || m.getMessageType() == MessageType.ASSISTANT)
                .map(m -> Map.of(
                        "role", m.getMessageType() == MessageType.USER ? "user" : "ai",
                        "content", m.getText()
                ))
                .toList();
    }

    /**
     * 回滚会话中最后一条无回复的用户消息
     * 当大模型调用失败时，Advisor 已将 user message 持久化但没有 assistant 回复，
     * 需要移除这条孤立的 user message，避免刷新后出现无答案的问题。
     */
    public void rollbackLastUserMessage(String sessionId) {
        List<Message> messages = chatMemoryRepository.findByConversationId(sessionId);
        if (messages.isEmpty()) return;

        Message last = messages.get(messages.size() - 1);
        if (last.getMessageType() == MessageType.USER) {
            List<Message> trimmed = new ArrayList<>(messages.subList(0, messages.size() - 1));
            chatMemoryRepository.saveAll(sessionId, trimmed);
            log.info("回滚孤立用户消息: sessionId={}, removedMessage={}",
                    sessionId, last.getText().length() > 50 ? last.getText().substring(0, 50) + "..." : last.getText());
        }
    }

    /**
     * 手动保存一轮对话到指定会话（用于 Multi-Agent 等非 Advisor 场景）
     */
    public void saveExchange(String sessionId, String userMessage, String aiResponse) {
        List<Message> existing = chatMemoryRepository.findByConversationId(sessionId);
        List<Message> updated = new ArrayList<>(existing);
        updated.add(new UserMessage(userMessage));
        updated.add(new AssistantMessage(aiResponse));
        chatMemoryRepository.saveAll(sessionId, updated);
        log.debug("保存对话记录: sessionId={}, totalMessages={}", sessionId, updated.size());
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
     * 查询指定用户+助手下的所有会话列表，每个会话附带摘要（第一条用户消息前30字）
     * sessionId 格式: {userId}-{agentType}-{timestamp}
     */
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

    /**
     * 统计当前持久化的会话数
     */
    public int getActiveSessionCount() {
        return chatMemoryRepository.findConversationIds().size();
    }
}
