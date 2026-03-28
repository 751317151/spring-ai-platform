package com.huah.ai.platform.gateway.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GatewayStreamEvent {
    private String chunk;
    private boolean done;
    private String model;
    private RouteDecisionPayload routeDecision;
}
