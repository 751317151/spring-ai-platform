package com.huah.ai.platform.monitor.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class ToolAuditView {
    String id;
    @JsonProperty("user_id")
    String userId;
    @JsonProperty("session_id")
    String sessionId;
    @JsonProperty("agent_type")
    String agentType;
    @JsonProperty("tool_name")
    String toolName;
    @JsonProperty("tool_class")
    String toolClass;
    @JsonProperty("input_summary")
    String inputSummary;
    @JsonProperty("output_summary")
    String outputSummary;
    boolean success;
    @JsonProperty("error_message")
    String errorMessage;
    @JsonProperty("latency_ms")
    long latencyMs;
    @JsonProperty("trace_id")
    String traceId;
    @JsonProperty("created_at")
    LocalDateTime createdAt;
}
