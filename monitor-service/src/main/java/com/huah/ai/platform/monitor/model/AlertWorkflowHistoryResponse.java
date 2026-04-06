package com.huah.ai.platform.monitor.model;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class AlertWorkflowHistoryResponse {
    String fingerprint;
    String workflowStatus;
    String workflowNote;
    LocalDateTime silencedUntil;
    LocalDateTime createdAt;
}

