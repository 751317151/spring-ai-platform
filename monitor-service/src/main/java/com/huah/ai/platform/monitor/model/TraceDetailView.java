package com.huah.ai.platform.monitor.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

@Value
@Builder
public class TraceDetailView {
    String id;
    @JsonProperty("trace_id")
    String traceId;
    @JsonProperty("user_id")
    String userId;
    @JsonProperty("agent_type")
    String agentType;
    @JsonProperty("model_id")
    String modelId;
    @JsonProperty("session_id")
    String sessionId;
    boolean success;
    @JsonProperty("error_message")
    String errorMessage;
    @JsonProperty("latency_ms")
    long latencyMs;
    @JsonProperty("prompt_tokens")
    Integer promptTokens;
    @JsonProperty("completion_tokens")
    Integer completionTokens;
    @JsonProperty("created_at")
    LocalDateTime createdAt;
    @JsonProperty("user_message")
    String userMessage;
    @JsonProperty("ai_response")
    String aiResponse;
    @JsonProperty("tool_executions")
    List<ToolAuditView> toolExecutions;
    @JsonProperty("phase_breakdown")
    List<TracePhaseView> phaseBreakdown;
}
