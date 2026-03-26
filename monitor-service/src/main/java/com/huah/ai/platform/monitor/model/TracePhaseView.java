package com.huah.ai.platform.monitor.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TracePhaseView {
    String key;
    String label;
    @JsonProperty("latency_ms")
    long latencyMs;
    double share;
    boolean estimated;
    String description;
}
