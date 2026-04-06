package com.huah.ai.platform.gateway.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class GatewayRouteDecisionResponse {
    private String scene;
    private String requestedModelId;
    private String selectedModelId;
    private String strategy;
    private String reason;
    private boolean fallbackTriggered;
    private String estimatedCostNote;
    private List<GatewayCandidateModelResponse> candidateModels;
}

