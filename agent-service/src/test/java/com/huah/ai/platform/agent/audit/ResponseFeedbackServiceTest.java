package com.huah.ai.platform.agent.audit;

import com.huah.ai.platform.common.exception.BizException;
import com.huah.ai.platform.agent.audit.AiAuditLog;
import com.huah.ai.platform.agent.audit.AiAuditLogMapper;
import com.huah.ai.platform.agent.audit.AiResponseFeedback;
import com.huah.ai.platform.agent.audit.AiResponseFeedbackMapper;
import com.huah.ai.platform.agent.audit.ResponseFeedbackService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResponseFeedbackServiceTest {

    @Mock
    private AiAuditLogMapper auditLogMapper;

    @Mock
    private AiResponseFeedbackMapper feedbackMapper;

    @InjectMocks
    private ResponseFeedbackService responseFeedbackService;

    @Test
    void shouldInsertFeedbackForOwnedResponse() {
        when(auditLogMapper.selectById("resp-1")).thenReturn(AiAuditLog.builder()
                .id("resp-1")
                .userId("user-1")
                .build());

        responseFeedbackService.submitAgentFeedback("user-1", "resp-1", "up", "great");

        ArgumentCaptor<AiResponseFeedback> captor = ArgumentCaptor.forClass(AiResponseFeedback.class);
        verify(feedbackMapper).insert((AiResponseFeedback) captor.capture());
        assertEquals("resp-1", captor.getValue().getResponseId());
        assertEquals("agent", captor.getValue().getSourceType());
        assertEquals("up", captor.getValue().getFeedback());
    }

    @Test
    void shouldRejectFeedbackForOtherUsersResponse() {
        when(auditLogMapper.selectById("resp-1")).thenReturn(AiAuditLog.builder()
                .id("resp-1")
                .userId("other-user")
                .build());

        assertThrows(BizException.class,
                () -> responseFeedbackService.submitAgentFeedback("user-1", "resp-1", "down", null));
        verify(feedbackMapper, never()).insert(any(AiResponseFeedback.class));
    }
}
