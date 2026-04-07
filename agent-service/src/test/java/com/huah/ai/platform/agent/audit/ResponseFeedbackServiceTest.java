package com.huah.ai.platform.agent.audit;

import com.huah.ai.platform.common.exception.BizException;
import com.huah.ai.platform.common.util.SnowflakeIdGenerator;
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

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @InjectMocks
    private ResponseFeedbackService responseFeedbackService;

    @Test
    void shouldInsertFeedbackForOwnedResponse() {
        when(snowflakeIdGenerator.nextLongId()).thenReturn(2001L);
        when(auditLogMapper.selectById(1001L)).thenReturn(AiAuditLogEntity.builder()
                .id(1001L)
                .userId("user-1")
                .build());

        responseFeedbackService.submitAgentFeedback("user-1", 1001L, "up", "great");

        ArgumentCaptor<AiResponseFeedbackEntity> captor = ArgumentCaptor.forClass(AiResponseFeedbackEntity.class);
        verify(feedbackMapper).insert(captor.capture());
        assertEquals(1001L, captor.getValue().getResponseId());
        assertEquals("agent", captor.getValue().getSourceType());
        assertEquals("up", captor.getValue().getFeedback());
    }

    @Test
    void shouldRejectFeedbackForOtherUsersResponse() {
        when(auditLogMapper.selectById(1001L)).thenReturn(AiAuditLogEntity.builder()
                .id(1001L)
                .userId("other-user")
                .build());

        assertThrows(BizException.class,
                () -> responseFeedbackService.submitAgentFeedback("user-1", 1001L, "down", null));
        verify(feedbackMapper, never()).insert(any(AiResponseFeedbackEntity.class));
    }
}
