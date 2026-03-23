package com.huah.ai.platform.agent.memory;

import com.huah.ai.platform.agent.metrics.AiMetricsCollector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationMemoryServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ChatMemoryRepository chatMemoryRepository;

    @Mock
    private AiMetricsCollector metricsCollector;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @InjectMocks
    private ConversationMemoryService conversationMemoryService;

    @Test
    void shouldCompressLongConversationIntoSummaryMessage() {
        List<Message> existing = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            existing.add(new UserMessage("用户问题-" + i));
            existing.add(new AssistantMessage("助手回答-" + i));
        }

        when(chatMemoryRepository.findByConversationId("session-1")).thenReturn(existing);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries(anyString())).thenReturn(Map.of());
        doNothing().when(hashOperations).putAll(anyString(), org.mockito.ArgumentMatchers.anyMap());
        when(redisTemplate.expire(anyString(), anyLong(), org.mockito.ArgumentMatchers.any())).thenReturn(true);

        conversationMemoryService.saveExchange("session-1", "最新用户问题", "最新助手回答");

        ArgumentCaptor<List<Message>> savedMessagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(chatMemoryRepository).saveAll(anyString(), savedMessagesCaptor.capture());

        List<Message> savedMessages = savedMessagesCaptor.getValue();
        assertFalse(savedMessages.isEmpty());
        assertTrue(savedMessages.get(0) instanceof AssistantMessage);
        assertTrue(savedMessages.get(0).getText().startsWith("[会话摘要]"));
        assertTrue(savedMessages.size() <= 13);
    }

    @Test
    void shouldListEncodedChineseUserSessionsOnly() {
        when(chatMemoryRepository.findConversationIds()).thenReturn(List.of(
                "%E5%BC%A0%E4%B8%89-rd-100",
                "%E5%BC%A0%E4%B8%89-rd-200",
                "alice-rd-100"
        ));
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries("ai:session:meta:%E5%BC%A0%E4%B8%89-rd-100")).thenReturn(Map.of(
                "summary", "旧会话",
                "updatedAt", "100",
                "pinned", "false",
                "archived", "false"
        ));
        when(hashOperations.entries("ai:session:meta:%E5%BC%A0%E4%B8%89-rd-200")).thenReturn(Map.of(
                "summary", "新会话",
                "updatedAt", "200",
                "pinned", "true",
                "archived", "false"
        ));

        List<Map<String, String>> sessions = conversationMemoryService.listSessions("%E5%BC%A0%E4%B8%89-rd-");

        assertEquals(2, sessions.size());
        assertEquals("%E5%BC%A0%E4%B8%89-rd-200", sessions.get(0).get("sessionId"));
        assertEquals("%E5%BC%A0%E4%B8%89-rd-100", sessions.get(1).get("sessionId"));
    }
}
