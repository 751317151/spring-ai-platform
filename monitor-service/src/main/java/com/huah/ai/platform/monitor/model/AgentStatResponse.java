package com.huah.ai.platform.monitor.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AgentStatResponse {
    @JsonProperty("agent_type")
    String agentType;
    long count;
    @JsonProperty("avg_latency")
    long avgLatency;
    long errors;
}

