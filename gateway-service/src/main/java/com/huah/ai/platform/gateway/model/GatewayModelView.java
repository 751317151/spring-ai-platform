package com.huah.ai.platform.gateway.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class GatewayModelView {
    private String id;
    private String name;
    private String provider;
    private boolean enabled;
    private Integer weight;
    private List<String> capabilities;
    private Integer rpmLimit;
    private Double promptCostPer1kTokens;
    private Double completionCostPer1kTokens;
    private String healthStatus;
    private Long degradedUntil;
    private String healthReason;
    private Integer consecutiveFailures;
    private Long lastCheckedAt;
    private Long lastProbeLatencyMs;
    private Integer totalCalls;
    private Integer successCalls;
    private Double avgLatencyMs;
    private Double successRate;
    private Long totalPromptTokens;
    private Long totalCompletionTokens;
    private Double totalEstimatedCost;
}
