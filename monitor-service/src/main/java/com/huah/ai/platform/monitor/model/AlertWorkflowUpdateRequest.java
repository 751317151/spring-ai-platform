package com.huah.ai.platform.monitor.model;

import lombok.Data;

@Data
public class AlertWorkflowUpdateRequest {
    private String workflowStatus;
    private String workflowNote;
    private String silencedUntil;
}
