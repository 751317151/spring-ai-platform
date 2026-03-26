package com.huah.ai.platform.rag.service;

import com.huah.ai.platform.common.exception.BizException;
import com.huah.ai.platform.rag.model.RagEvaluationOverview;
import com.huah.ai.platform.rag.model.RagEvaluationSample;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

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
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private RagAuditService ragAuditService;

    @Test
    void shouldRejectFeedbackForOtherUsersResponse() {
        when(jdbcTemplate.query(eq("SELECT user_id FROM ai_audit_logs WHERE id = ?"), any(ResultSetExtractor.class), eq("resp-1")))
                .thenReturn("other-user");

        assertThrows(BizException.class,
                () -> ragAuditService.submitFeedback("user-1", "resp-1", "kb-1", "up", null));

        verify(jdbcTemplate, never()).update(eq("UPDATE ai_response_feedback SET feedback = ?, comment = ?, updated_at = ?, knowledge_base_id = ? WHERE response_id = ?"),
                any(), any(), any(), any(), any());
    }

    @Test
    void shouldRejectEvidenceFeedbackForOtherUsersResponse() {
        when(jdbcTemplate.query(eq("SELECT user_id FROM ai_audit_logs WHERE id = ?"), any(ResultSetExtractor.class), eq("resp-1")))
                .thenReturn("other-user");

        assertThrows(BizException.class,
                () -> ragAuditService.submitEvidenceFeedback("user-1", "resp-1", "chunk-1", "kb-1", "down", null));

        verify(jdbcTemplate, never()).update(eq("UPDATE ai_evidence_feedback SET feedback = ?, comment = ?, knowledge_base_id = ?, updated_at = ? WHERE response_id = ? AND chunk_id = ?"),
                any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldBuildEvaluationOverview() {
        when(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ai_audit_logs WHERE agent_type = 'rag'", Long.class))
                .thenReturn(10L);
        when(jdbcTemplate.queryForMap(org.mockito.ArgumentMatchers.contains("FROM ai_response_feedback")))
                .thenReturn(Map.of("total", 4L, "positive", 3L, "negative", 1L));
        when(jdbcTemplate.queryForMap(org.mockito.ArgumentMatchers.contains("FROM ai_evidence_feedback")))
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
        RagEvaluationSample sample = RagEvaluationSample.builder()
                .responseId("resp-1")
                .userId("user-1")
                .knowledgeBaseId("kb-1")
                .question("q")
                .answer("a")
                .feedback("down")
                .comment("bad")
                .evidenceNegativeCount(2)
                .createdAt(LocalDateTime.now())
                .build();
        when(jdbcTemplate.query(org.mockito.ArgumentMatchers.contains("FROM ai_audit_logs a"), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(sample));

        List<RagEvaluationSample> result = ragAuditService.getLowRatedSamples(null, 10);

        assertEquals(1, result.size());
        assertEquals("resp-1", result.get(0).getResponseId());
        assertEquals(2L, result.get(0).getEvidenceNegativeCount());
    }
}
