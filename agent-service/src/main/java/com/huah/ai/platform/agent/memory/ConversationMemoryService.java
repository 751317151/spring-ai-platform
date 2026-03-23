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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationMemoryService {

    private static final String USER_PROFILE_KEY = "ai:user:profile:";
    private static final String SESSION_META_KEY = "ai:session:meta:";
    private static final String SUMMARY_KEY = "summary";
    private static final String UPDATED_AT_KEY = "updatedAt";
    private static final String PINNED_KEY = "pinned";
    private static final String ARCHIVED_KEY = "archived";
    private static final String DEFAULT_SESSION_TITLE = "新对话";
    private static final String CONVERSATION_SUMMARY_PREFIX = "[会话摘要]\n";
    private static final int MAX_MESSAGES_BEFORE_COMPRESSION = 24;
    private static final int RECENT_MESSAGES_TO_KEEP = 12;
    private static final int MAX_SUMMARY_ENTRY_LENGTH = 120;
    private static final int MAX_SUMMARY_LENGTH = 1200;

    private final StringRedisTemplate redisTemplate;
    private final ChatMemoryRepository chatMemoryRepository;
    private final AiMetricsCollector metricsCollector;

    public ChatMemory getOrCreateMemory(String sessionId) {
        log.debug("Load conversation memory: sessionId={}", sessionId);
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(20)
                .build();
    }

    public void clearMemory(String sessionId) {
        chatMemoryRepository.deleteByConversationId(sessionId);
        redisTemplate.delete(SESSION_META_KEY + sessionId);
        log.debug("Cleared conversation memory: sessionId={}", sessionId);
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
            log.info("Rolled back dangling user message: sessionId={}, removedMessage={}",
                    sessionId, truncate(last.getText(), 50));
        }
    }

    public void saveExchange(String sessionId, String userMessage, String aiResponse) {
        List<Message> existing = chatMemoryRepository.findByConversationId(sessionId);
        List<Message> updated = new ArrayList<>(existing);
        updated.add(new UserMessage(userMessage));
        updated.add(new AssistantMessage(aiResponse));
        List<Message> compressed = maybeCompressConversation(updated);
        chatMemoryRepository.saveAll(sessionId, compressed);
        updateSessionMetadata(sessionId, buildSummary(userMessage), null, null);
        log.debug("Saved exchange: sessionId={}, totalMessages={}", sessionId, compressed.size());
    }

    public void renameSession(String sessionId, String summary) {
        updateSessionMetadata(sessionId, summary, null, null);
    }

    public void pinSession(String sessionId, boolean pinned) {
        updateSessionMetadata(sessionId, null, pinned, null);
    }

    public void archiveSession(String sessionId, boolean archived) {
        updateSessionMetadata(sessionId, null, null, archived);
    }

    public void saveUserProfile(String userId, String key, String value) {
        String redisKey = USER_PROFILE_KEY + userId;
        try {
            redisTemplate.opsForHash().put(redisKey, key, value);
            redisTemplate.expire(redisKey, 30, TimeUnit.DAYS);
            log.debug("Updated user profile: userId={}, key={}", userId, key);
        } catch (Exception e) {
            metricsCollector.recordDependencyFailure("redis", "save-user-profile");
            log.warn("Redis user profile write failed: userId={}, key={}, error={}", userId, key, e.getMessage());
        }
    }

    public String getUserProfile(String userId, String key) {
        String redisKey = USER_PROFILE_KEY + userId;
        try {
            Object value = redisTemplate.opsForHash().get(redisKey, key);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            metricsCollector.recordDependencyFailure("redis", "get-user-profile");
            log.warn("Redis user profile read failed: userId={}, key={}, error={}", userId, key, e.getMessage());
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

            StringBuilder builder = new StringBuilder("用户个人信息:\n");
            profile.forEach((key, value) -> builder.append("- ").append(key).append(": ").append(value).append("\n"));
            return builder.toString();
        } catch (Exception e) {
            metricsCollector.recordDependencyFailure("redis", "build-personalized-context");
            log.warn("Redis personalized context build failed: userId={}, error={}", userId, e.getMessage());
            return "";
        }
    }

    public List<Map<String, String>> listSessions(String prefix) {
        return chatMemoryRepository.findConversationIds().stream()
                .filter(id -> id.startsWith(prefix))
                .map(this::buildSessionInfo)
                .sorted(Comparator
                        .comparing((Map<String, String> session) -> Boolean.parseBoolean(session.getOrDefault(ARCHIVED_KEY, "false")))
                        .thenComparing((Map<String, String> session) -> Boolean.parseBoolean(session.getOrDefault(PINNED_KEY, "false")), Comparator.reverseOrder())
                        .thenComparing(session -> session.getOrDefault(UPDATED_AT_KEY, "0"), Comparator.reverseOrder()))
                .toList();
    }

    public int getActiveSessionCount() {
        return chatMemoryRepository.findConversationIds().size();
    }

    private List<Message> maybeCompressConversation(List<Message> messages) {
        if (messages.size() <= MAX_MESSAGES_BEFORE_COMPRESSION) {
            return messages;
        }

        int splitIndex = Math.max(messages.size() - RECENT_MESSAGES_TO_KEEP, 1);
        List<Message> head = new ArrayList<>(messages.subList(0, splitIndex));
        List<Message> tail = new ArrayList<>(messages.subList(splitIndex, messages.size()));

        String existingSummary = "";
        if (!head.isEmpty() && isSummaryMessage(head.get(0))) {
            existingSummary = stripSummaryPrefix(head.remove(0).getText());
        }

        if (head.isEmpty()) {
            return messages;
        }

        String summary = buildConversationSummary(existingSummary, head);
        List<Message> compressed = new ArrayList<>();
        compressed.add(new AssistantMessage(summary));
        compressed.addAll(tail);
        return compressed;
    }

    private String buildConversationSummary(String existingSummary, List<Message> messages) {
        StringBuilder builder = new StringBuilder(CONVERSATION_SUMMARY_PREFIX);
        if (!existingSummary.isBlank()) {
            builder.append(existingSummary).append("\n");
        }

        for (Message message : messages) {
            if (message.getMessageType() != MessageType.USER && message.getMessageType() != MessageType.ASSISTANT) {
                continue;
            }
            String role = message.getMessageType() == MessageType.USER ? "用户" : "助手";
            builder.append("- ")
                    .append(role)
                    .append(": ")
                    .append(truncate(normalizeSummary(message.getText()), MAX_SUMMARY_ENTRY_LENGTH))
                    .append("\n");

            if (builder.length() >= MAX_SUMMARY_LENGTH) {
                builder.append("- 更多历史内容已压缩\n");
                break;
            }
        }

        return truncate(builder.toString().trim(), MAX_SUMMARY_LENGTH);
    }

    private boolean isSummaryMessage(Message message) {
        return message.getMessageType() == MessageType.ASSISTANT
                && message.getText() != null
                && message.getText().startsWith(CONVERSATION_SUMMARY_PREFIX);
    }

    private String stripSummaryPrefix(String text) {
        return text != null && text.startsWith(CONVERSATION_SUMMARY_PREFIX)
                ? text.substring(CONVERSATION_SUMMARY_PREFIX.length())
                : text;
    }

    private Map<String, String> buildSessionInfo(String sessionId) {
        Map<Object, Object> metadata = loadSessionMetadata(sessionId);

        Map<String, String> session = new HashMap<>();
        session.put("sessionId", sessionId);
        session.put("summary", String.valueOf(metadata.getOrDefault(SUMMARY_KEY, deriveSummary(sessionId))));
        session.put("updatedAt", String.valueOf(metadata.getOrDefault(UPDATED_AT_KEY, "0")));
        session.put("pinned", String.valueOf(metadata.getOrDefault(PINNED_KEY, "false")));
        session.put("archived", String.valueOf(metadata.getOrDefault(ARCHIVED_KEY, "false")));
        return session;
    }

    private Map<Object, Object> loadSessionMetadata(String sessionId) {
        try {
            return redisTemplate.opsForHash().entries(SESSION_META_KEY + sessionId);
        } catch (Exception e) {
            metricsCollector.recordDependencyFailure("redis", "load-session-metadata");
            log.warn("Redis session metadata read failed: sessionId={}, error={}", sessionId, e.getMessage());
            return Map.of();
        }
    }

    private void updateSessionMetadata(String sessionId, String summary, Boolean pinned, Boolean archived) {
        try {
            String redisKey = SESSION_META_KEY + sessionId;
            Map<Object, Object> existing = redisTemplate.opsForHash().entries(redisKey);
            Map<String, String> metadata = new HashMap<>();
            metadata.put(SUMMARY_KEY, summary != null
                    ? normalizeSummary(summary)
                    : String.valueOf(existing.getOrDefault(SUMMARY_KEY, deriveSummary(sessionId))));
            metadata.put(PINNED_KEY, pinned != null
                    ? String.valueOf(pinned)
                    : String.valueOf(existing.getOrDefault(PINNED_KEY, "false")));
            metadata.put(ARCHIVED_KEY, archived != null
                    ? String.valueOf(archived)
                    : String.valueOf(existing.getOrDefault(ARCHIVED_KEY, "false")));
            metadata.put(UPDATED_AT_KEY, String.valueOf(System.currentTimeMillis()));
            redisTemplate.opsForHash().putAll(redisKey, metadata);
            redisTemplate.expire(redisKey, 30, TimeUnit.DAYS);
        } catch (Exception e) {
            metricsCollector.recordDependencyFailure("redis", "save-session-metadata");
            log.warn("Redis session metadata write failed: sessionId={}, error={}", sessionId, e.getMessage());
        }
    }

    private String deriveSummary(String sessionId) {
        return chatMemoryRepository.findByConversationId(sessionId).stream()
                .filter(m -> m.getMessageType() == MessageType.USER)
                .map(Message::getText)
                .findFirst()
                .map(this::buildSummary)
                .orElse(DEFAULT_SESSION_TITLE);
    }

    private String buildSummary(String text) {
        String normalized = normalizeSummary(text);
        return normalized.length() > 30 ? normalized.substring(0, 30) + "..." : normalized;
    }

    private String normalizeSummary(String text) {
        if (text == null || text.isBlank()) {
            return DEFAULT_SESSION_TITLE;
        }
        return text.replaceAll("\\s+", " ").trim();
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "null";
        }
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }
}
