package com.huah.ai.platform.gateway.model;

import com.huah.ai.platform.gateway.service.ModelGatewayService;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RouteDecisionPayload {
    private String scene;
    private String requestedModelId;
    private String selectedModelId;
    private String strategy;
    private String reason;
    private List<String> candidateModelIds;
    private List<String> healthyCandidateModelIds;
    private List<String> degradedModelIds;
    private boolean fallbackTriggered;

    public static RouteDecisionPayload from(ModelGatewayService.RouteDecision decision) {
        return RouteDecisionPayload.builder()
                .scene(decision.getScene() == null ? "" : decision.getScene())
                .requestedModelId(decision.getRequestedModelId() == null ? "" : decision.getRequestedModelId())
                .selectedModelId(decision.getSelectedModelId() == null ? "" : decision.getSelectedModelId())
                .strategy(decision.getStrategy() == null ? "" : decision.getStrategy())
                .reason(decision.getReason() == null ? "" : decision.getReason())
                .candidateModelIds(decision.getCandidateModelIds() == null ? List.of() : decision.getCandidateModelIds())
                .healthyCandidateModelIds(
                        decision.getHealthyCandidateModelIds() == null ? List.of() : decision.getHealthyCandidateModelIds())
                .degradedModelIds(decision.getDegradedModelIds() == null ? List.of() : decision.getDegradedModelIds())
                .fallbackTriggered(decision.isFallbackTriggered())
                .build();
    }
}
