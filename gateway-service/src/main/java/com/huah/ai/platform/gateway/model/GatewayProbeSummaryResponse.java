package com.huah.ai.platform.gateway.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class GatewayProbeSummaryResponse {
    private List<GatewayModelProbeResponse> probes;
    private int count;
}
