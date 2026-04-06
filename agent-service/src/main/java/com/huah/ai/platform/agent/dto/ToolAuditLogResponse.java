package com.huah.ai.platform.agent.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class ToolAuditLogResponse {
    Long id;
    String userId;
    String sessionId;
    String agentType;
    String toolName;
    String toolClass;
    String inputSummary;
    String outputSummary;
    Boolean success;
    String errorMessage;
    String reasonCode;
    String deniedResource;
    Long latencyMs;
    String traceId;
    LocalDateTime createdAt;
}
