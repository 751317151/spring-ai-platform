package com.huah.ai.platform.gateway.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GatewayModelProbeResponse {
    private String modelId;
    private String status;
    private long probeLatencyMs;
    private String reason;
}
