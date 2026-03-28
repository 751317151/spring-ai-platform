package com.huah.ai.platform.agent.learning;

import com.huah.ai.platform.agent.dto.SessionConfigRequest;
import com.huah.ai.platform.agent.learning.dto.FollowUpTemplatePayload;
import com.huah.ai.platform.agent.learning.dto.LearningFavoritePayload;
import com.huah.ai.platform.agent.learning.dto.LearningNotePayload;
import com.huah.ai.platform.agent.support.AgentTestFixtures;
import com.huah.ai.platform.common.util.SnowflakeIdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearningCenterServiceTest {

    @Mock
    private LearningFavoriteMapper favoriteMapper;

    @Mock
    private LearningNoteMapper noteMapper;

    @Mock
    private FollowUpTemplateMapper templateMapper;

    private LearningCenterService service;

    @BeforeEach
    void setUp() {
        service = new LearningCenterService(
                favoriteMapper,
                noteMapper,
                templateMapper,
                AgentTestFixtures.objectMapper(),
                new SnowflakeIdGenerator()
        );
    }

    @Test
    void saveFavoriteInsertsNewRecordAndPersistsJsonFields() {
        LearningFavoritePayload payload = new LearningFavoritePayload();
        payload.setId("fav-1");
        payload.setRole("assistant");
        payload.setContent("answer");
        payload.setAgentType("rd");
        payload.setSessionId("session-1");
        payload.setSessionSummary("排查记录");
        payload.setSourceMessageIndex(2);
        payload.setCreatedAt(10L);
        payload.setLastCollectedAt(20L);
        payload.setDuplicateCount(1);
        payload.setTags(List.of("登录", "排查"));
        SessionConfigRequest sessionConfig = new SessionConfigRequest();
        sessionConfig.setModel("gpt-4.1");
        sessionConfig.setKnowledgeEnabled(Boolean.TRUE);
        payload.setSessionConfigSnapshot(sessionConfig);

        when(favoriteMapper.selectOne(any())).thenReturn(null);
        when(favoriteMapper.selectByUserId("user-1")).thenReturn(List.of());

        service.saveFavorite("user-1", payload);

        ArgumentCaptor<LearningFavoriteRecord> captor = ArgumentCaptor.forClass(LearningFavoriteRecord.class);
        verify(favoriteMapper).insert(captor.capture());
        verify(favoriteMapper, never()).updateById(any(LearningFavoriteRecord.class));
        LearningFavoriteRecord saved = captor.getValue();
        assertEquals("user-1", saved.getUserId());
        assertEquals("fav-1", saved.getId());
        assertTrue(saved.getTagsJson().contains("登录"));
        assertTrue(saved.getSessionConfigSnapshotJson().contains("gpt-4.1"));
    }

    @Test
    void listFavoritesMapsTagsAndSessionConfig() {
        when(favoriteMapper.selectByUserId("user-1")).thenReturn(List.of(
                LearningFavoriteRecord.builder()
                        .id("fav-1")
                        .userId("user-1")
                        .role("assistant")
                        .content("answer")
                        .tagsJson("[\"登录\",\"排查\"]")
                        .sessionConfigSnapshotJson("{\"model\":\"gpt-4.1\",\"knowledgeEnabled\":true}")
                        .build()
        ));

        List<LearningFavoritePayload> result = service.listFavorites("user-1");

        assertEquals(1, result.size());
        assertEquals(List.of("登录", "排查"), result.get(0).getTags());
        assertEquals("gpt-4.1", result.get(0).getSessionConfigSnapshot().getModel());
        assertEquals(Boolean.TRUE, result.get(0).getSessionConfigSnapshot().getKnowledgeEnabled());
    }

    @Test
    void listNotesFallsBackToEmptyTagsWhenJsonBroken() {
        when(noteMapper.selectByUserId("user-1")).thenReturn(List.of(
                LearningNoteRecord.builder()
                        .id("note-1")
                        .userId("user-1")
                        .title("note")
                        .content("content")
                        .tagsJson("not-json")
                        .build()
        ));

        List<LearningNotePayload> result = service.listNotes("user-1");

        assertEquals(1, result.size());
        assertTrue(result.get(0).getTags().isEmpty());
    }

    @Test
    void saveTemplateUpdatesExistingRecord() {
        FollowUpTemplatePayload payload = new FollowUpTemplatePayload();
        payload.setId("tpl-1");
        payload.setName("追问模板");
        payload.setContent("继续展开");
        payload.setSourceCount(2);
        payload.setUpdatedAt(100L);

        when(templateMapper.selectOne(any())).thenReturn(FollowUpTemplateRecord.builder().id("tpl-1").userId("user-1").build());
        when(templateMapper.selectByUserId("user-1")).thenReturn(List.of());

        service.saveTemplate("user-1", payload);

        ArgumentCaptor<FollowUpTemplateRecord> captor = ArgumentCaptor.forClass(FollowUpTemplateRecord.class);
        verify(templateMapper).updateById(captor.capture());
        assertEquals("user-1", captor.getValue().getUserId());
        assertEquals("追问模板", captor.getValue().getName());
    }

    @Test
    void deleteNoteDeletesOnlyCurrentUserRecord() {
        service.deleteNote("user-1", "note-1");
        verify(noteMapper).deleteByUserIdAndId("user-1", "note-1");
    }

    @Test
    void listTemplatesReturnsEmptyWhenNoData() {
        when(templateMapper.selectByUserId("user-1")).thenReturn(List.of());
        List<FollowUpTemplatePayload> result = service.listTemplates("user-1");
        assertTrue(result.isEmpty());
    }
}
