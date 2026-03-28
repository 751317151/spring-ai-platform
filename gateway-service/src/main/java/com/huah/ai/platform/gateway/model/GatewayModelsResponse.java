package com.huah.ai.platform.gateway.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class GatewayModelsResponse {
    private List<GatewayModelView> models;
    private int count;
    private Map<String, List<String>> sceneRoutes;
    private String loadBalanceStrategy;
}
