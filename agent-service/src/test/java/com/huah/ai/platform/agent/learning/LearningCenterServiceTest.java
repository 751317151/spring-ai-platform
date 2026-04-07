package com.huah.ai.platform.agent.learning;

import com.huah.ai.platform.agent.dto.SessionConfigRequest;
import com.huah.ai.platform.agent.learning.dto.FollowUpTemplateRequest;
import com.huah.ai.platform.agent.learning.dto.FollowUpTemplateResponse;
import com.huah.ai.platform.agent.learning.dto.LearningFavoriteRequest;
import com.huah.ai.platform.agent.learning.dto.LearningFavoriteResponse;
import com.huah.ai.platform.agent.learning.dto.LearningNoteResponse;
import com.huah.ai.platform.agent.support.AgentTestFixtures;
import com.huah.ai.platform.common.util.SnowflakeIdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        LearningFavoriteRequest request = new LearningFavoriteRequest();
        request.setId(1001L);
        request.setRole("assistant");
        request.setContent("answer");
        request.setAgentType("rd");
        request.setSessionId("session-1");
        request.setSessionSummary("排查记录");
        request.setSourceMessageIndex(2);
        request.setLastCollectedAt(LocalDateTime.of(2026, 4, 1, 10, 20));
        request.setDuplicateCount(1);
        request.setTags(List.of("登录", "排查"));
        SessionConfigRequest sessionConfig = new SessionConfigRequest();
        sessionConfig.setModel("gpt-4.1");
        sessionConfig.setKnowledgeEnabled(Boolean.TRUE);
        request.setSessionConfigSnapshot(sessionConfig);

        when(favoriteMapper.selectOne(any())).thenReturn(null);
        when(favoriteMapper.selectByUserId("user-1")).thenReturn(List.of());

        service.saveFavorite("user-1", request);

        ArgumentCaptor<LearningFavoriteEntity> captor = ArgumentCaptor.forClass(LearningFavoriteEntity.class);
        verify(favoriteMapper).insert(captor.capture());
        verify(favoriteMapper, never()).updateById(any(LearningFavoriteEntity.class));
        LearningFavoriteEntity saved = captor.getValue();
        assertEquals("user-1", saved.getUserId());
        assertEquals(1001L, saved.getId());
        assertTrue(saved.getTagsJson().contains("登录"));
        assertTrue(saved.getSessionConfigSnapshotJson().contains("gpt-4.1"));
    }

    @Test
    void listFavoritesMapsTagsAndSessionConfig() {
        when(favoriteMapper.selectByUserId("user-1")).thenReturn(List.of(
                LearningFavoriteEntity.builder()
                        .id(1001L)
                        .userId("user-1")
                        .role("assistant")
                        .content("answer")
                        .tagsJson("[\"登录\",\"排查\"]")
                        .sessionConfigSnapshotJson("{\"model\":\"gpt-4.1\",\"knowledgeEnabled\":true}")
                        .build()
        ));

        List<LearningFavoriteResponse> result = service.listFavorites("user-1");

        assertEquals(1, result.size());
        assertEquals(List.of("登录", "排查"), result.get(0).getTags());
        assertEquals("gpt-4.1", result.get(0).getSessionConfigSnapshot().getModel());
        assertEquals(Boolean.TRUE, result.get(0).getSessionConfigSnapshot().getKnowledgeEnabled());
    }

    @Test
    void listNotesFallsBackToEmptyTagsWhenJsonBroken() {
        when(noteMapper.selectByUserId("user-1")).thenReturn(List.of(
                LearningNoteEntity.builder()
                        .id(2001L)
                        .userId("user-1")
                        .title("note")
                        .content("content")
                        .tagsJson("not-json")
                        .build()
        ));

        List<LearningNoteResponse> result = service.listNotes("user-1");

        assertEquals(1, result.size());
        assertTrue(result.get(0).getTags().isEmpty());
    }

    @Test
    void saveTemplateUpdatesExistingRecord() {
        FollowUpTemplateRequest request = new FollowUpTemplateRequest();
        request.setId(3001L);
        request.setName("追问模板");
        request.setContent("继续展开");
        request.setSourceCount(2);

        when(templateMapper.selectOne(any())).thenReturn(
                FollowUpTemplateEntity.builder().id(3001L).userId("user-1").build());
        when(templateMapper.selectByUserId("user-1")).thenReturn(List.of());

        service.saveTemplate("user-1", request);

        ArgumentCaptor<FollowUpTemplateEntity> captor = ArgumentCaptor.forClass(FollowUpTemplateEntity.class);
        verify(templateMapper).updateById(captor.capture());
        assertEquals("user-1", captor.getValue().getUserId());
        assertEquals("追问模板", captor.getValue().getName());
    }

    @Test
    void deleteNoteDeletesOnlyCurrentUserRecord() {
        service.deleteNote("user-1", "2001");
        verify(noteMapper).deleteByUserIdAndId("user-1", 2001L);
    }

    @Test
    void listTemplatesReturnsEmptyWhenNoData() {
        when(templateMapper.selectByUserId("user-1")).thenReturn(List.of());
        List<FollowUpTemplateResponse> result = service.listTemplates("user-1");
        assertTrue(result.isEmpty());
    }
}
