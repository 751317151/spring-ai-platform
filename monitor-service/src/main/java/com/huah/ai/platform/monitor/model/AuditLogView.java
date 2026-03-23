package com.huah.ai.platform.monitor.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class AuditLogView {
    String id;
    @JsonProperty("user_id")
    String userId;
    @JsonProperty("agent_type")
    String agentType;
    @JsonProperty("model_id")
    String modelId;
    @JsonProperty("error_message")
    String errorMessage;
    @JsonProperty("session_id")
    String sessionId;
    @JsonProperty("latency_ms")
    long latencyMs;
    boolean success;
    @JsonProperty("created_at")
    LocalDateTime createdAt;
}
