package com.huah.ai.platform.agent.memory;

import com.huah.ai.platform.agent.dto.SessionConfigRequest;
import com.huah.ai.platform.agent.dto.SessionConfigResponse;
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

    @Test
    void shouldSearchSessionsByKeywordAndPinnedStatus() {
        when(chatMemoryRepository.findConversationIds()).thenReturn(List.of(
                "alice-rd-100",
                "alice-rd-200",
                "alice-rd-300"
        ));
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries("ai:session:meta:alice-rd-100")).thenReturn(Map.of(
                "summary", "发布复盘",
                "updatedAt", "100",
                "pinned", "false",
                "archived", "false"
        ));
        when(hashOperations.entries("ai:session:meta:alice-rd-200")).thenReturn(Map.of(
                "summary", "发布计划",
                "updatedAt", "200",
                "pinned", "true",
                "archived", "false"
        ));
        when(hashOperations.entries("ai:session:meta:alice-rd-300")).thenReturn(Map.of(
                "summary", "归档问题单",
                "updatedAt", "300",
                "pinned", "true",
                "archived", "true"
        ));

        List<Map<String, String>> sessions = conversationMemoryService.searchSessions(
                "alice-rd-",
                "发布",
                false,
                true,
                null,
                null,
                10
        );

        assertEquals(1, sessions.size());
        assertEquals("alice-rd-200", sessions.get(0).get("sessionId"));
    }

    @Test
    void shouldSaveAndReadSessionConfig() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries("ai:session:meta:alice-rd-100")).thenReturn(Map.of(
                "summary", "已有会话",
                "updatedAt", "100",
                "pinned", "false",
                "archived", "false",
                "model", "gpt-4.1",
                "temperature", "0.3",
                "maxContextMessages", "16",
                "knowledgeEnabled", "true",
                "systemPromptTemplate", "你是发布助手"
        ));
        doNothing().when(hashOperations).putAll(anyString(), org.mockito.ArgumentMatchers.anyMap());
        when(redisTemplate.expire(anyString(), anyLong(), org.mockito.ArgumentMatchers.any())).thenReturn(true);

        SessionConfigRequest request = new SessionConfigRequest();
        request.setModel("gpt-4.1");
        request.setTemperature(0.3);
        request.setMaxContextMessages(16);
        request.setKnowledgeEnabled(true);
        request.setSystemPromptTemplate("你是发布助手");

        conversationMemoryService.saveSessionConfig("alice-rd-100", request);
        SessionConfigResponse config = conversationMemoryService.getSessionConfig("alice-rd-100");

        verify(hashOperations).putAll(anyString(), org.mockito.ArgumentMatchers.anyMap());
        assertEquals("gpt-4.1", config.getModel());
        assertEquals(0.3, config.getTemperature());
        assertEquals(16, config.getMaxContextMessages());
        assertTrue(Boolean.TRUE.equals(config.getKnowledgeEnabled()));
        assertEquals("你是发布助手", config.getSystemPromptTemplate());
    }

    @Test
    void shouldStripRuntimeInstructionFromHistoryUserMessages() {
        when(chatMemoryRepository.findByConversationId("session-1")).thenReturn(List.of(
                new UserMessage("[会话配置]\n- 知识增强：开启\n- 上下文窗口：最近 6 条消息。\n\n[user-question]\n真正的问题"),
                new AssistantMessage("助手回答")
        ));

        List<Map<String, String>> history = conversationMemoryService.getHistory("session-1");

        assertEquals(2, history.size());
        assertEquals("真正的问题", history.get(0).get("content"));
        assertEquals("助手回答", history.get(1).get("content"));
    }
}
