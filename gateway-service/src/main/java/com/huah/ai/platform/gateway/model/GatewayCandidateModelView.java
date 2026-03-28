package com.huah.ai.platform.gateway.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GatewayCandidateModelView {
    private String id;
    private String name;
    private String provider;
    private boolean enabled;
    private boolean healthy;
    private boolean selected;
    private boolean degraded;
    private Integer weight;
    private Long avgLatencyMs;
    private Double successRate;
    private Double promptCostPer1kTokens;
    private Double completionCostPer1kTokens;
    private String reason;
}
