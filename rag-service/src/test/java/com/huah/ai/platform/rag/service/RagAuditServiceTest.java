package com.huah.ai.platform.rag.service;

import com.huah.ai.platform.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;
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
}
