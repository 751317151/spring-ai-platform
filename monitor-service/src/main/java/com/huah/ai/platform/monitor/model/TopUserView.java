package com.huah.ai.platform.monitor.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TopUserView {
    @JsonProperty("user_id")
    String userId;
    @JsonProperty("agent_type")
    String agentType;
    long calls;
    @JsonProperty("avg_latency")
    long avgLatency;
}
