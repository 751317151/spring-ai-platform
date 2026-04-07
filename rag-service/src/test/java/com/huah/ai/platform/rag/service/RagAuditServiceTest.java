package com.huah.ai.platform.rag.service;

import com.huah.ai.platform.common.exception.BizException;
import com.huah.ai.platform.common.persistence.audit.AiAuditLogEntity;
import com.huah.ai.platform.common.persistence.audit.AiEvidenceFeedbackEntity;
import com.huah.ai.platform.common.persistence.audit.AiAuditLogMapper;
import com.huah.ai.platform.common.persistence.audit.AiEvidenceFeedbackMapper;
import com.huah.ai.platform.common.persistence.audit.AiResponseFeedbackEntity;
import com.huah.ai.platform.common.persistence.audit.AiResponseFeedbackMapper;
import com.huah.ai.platform.common.util.SnowflakeIdGenerator;
import com.huah.ai.platform.rag.model.RagEvaluationOverview;
import com.huah.ai.platform.rag.model.RagEvaluationSample;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagAuditServiceTest {

    @Mock
    private AiAuditLogMapper auditLogMapper;

    @Mock
    private AiResponseFeedbackMapper responseFeedbackMapper;

    @Mock
    private AiEvidenceFeedbackMapper evidenceFeedbackMapper;

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @InjectMocks
    private RagAuditService ragAuditService;

    @Test
    void shouldRejectFeedbackForOtherUsersResponse() {
        when(auditLogMapper.selectById(1001L))
                .thenReturn(AiAuditLogEntity.builder().id(1001L).userId("other-user").build());

        assertThrows(BizException.class,
                () -> ragAuditService.submitFeedback("user-1", 1001L, 2001L, "up", null));

        verify(responseFeedbackMapper, never()).insert(any(AiResponseFeedbackEntity.class));
        verify(responseFeedbackMapper, never()).updateById(any(AiResponseFeedbackEntity.class));
    }

    @Test
    void shouldRejectEvidenceFeedbackForOtherUsersResponse() {
        when(auditLogMapper.selectById(1001L))
                .thenReturn(AiAuditLogEntity.builder().id(1001L).userId("other-user").build());

        assertThrows(BizException.class,
                () -> ragAuditService.submitEvidenceFeedback("user-1", 1001L, "chunk-1", 2001L, "down", null));

        verify(evidenceFeedbackMapper, never()).insert(any(AiEvidenceFeedbackEntity.class));
        verify(evidenceFeedbackMapper, never()).updateById(any(AiEvidenceFeedbackEntity.class));
    }

    @Test
    void shouldBuildEvaluationOverview() {
        when(auditLogMapper.countRagQueries(null)).thenReturn(10L);
        when(responseFeedbackMapper.countStatsBySourceTypeAndKnowledgeBaseId("rag", null))
                .thenReturn(Map.of("total", 4L, "positive", 3L, "negative", 1L));
        when(evidenceFeedbackMapper.countStatsByKnowledgeBaseId(null))
                .thenReturn(Map.of("total", 5L, "positive", 2L, "negative", 3L));

        RagEvaluationOverview overview = ragAuditService.getEvaluationOverview(null);

        assertEquals(10L, overview.getTotalQueries());
        assertEquals(4L, overview.getFeedbackCount());
        assertEquals(3L, overview.getPositiveFeedbackCount());
        assertEquals(5L, overview.getEvidenceFeedbackCount());
        assertEquals(4L, overview.getLowRatedQueryCount());
        assertTrue(overview.getPositiveFeedbackRate() > 0.7d);
    }

    @Test
    void shouldReturnLowRatedSamples() {
        when(auditLogMapper.selectLowRatedRagSamples(eq(null), eq(10)))
                .thenReturn(List.of(Map.of(
                        "responseId", 1001L,
                        "userId", "user-1",
                        "knowledgeBaseId", 2001L,
                        "question", "q",
                        "answer", "a",
                        "feedback", "down",
                        "comment", "bad",
                        "evidenceNegativeCount", 2L,
                        "createdAt", LocalDateTime.now()
                )));

        List<RagEvaluationSample> result = ragAuditService.getLowRatedSamples(null, 10);

        assertEquals(1, result.size());
        assertEquals(1001L, result.get(0).getResponseId());
        assertEquals(2L, result.get(0).getEvidenceNegativeCount());
    }
}
