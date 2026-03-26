package com.huah.ai.platform.agent.audit;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TracePhaseRecord {
    String key;
    String label;
    long latencyMs;
    double share;
    boolean estimated;
    String description;
}
