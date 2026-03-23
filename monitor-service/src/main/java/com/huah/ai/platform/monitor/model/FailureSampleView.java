package com.huah.ai.platform.monitor.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class FailureSampleView {
    private String id;
    @JsonProperty("user_id")
    private String userId;
    @JsonProperty("agent_type")
    private String agentType;
    @JsonProperty("model_id")
    private String modelId;
    @JsonProperty("error_message")
    private String errorMessage;
    @JsonProperty("latency_ms")
    private long latencyMs;
    @JsonProperty("session_id")
    private String sessionId;
    @JsonProperty("created_at")
    private LocalDateTime createdAt;
}
