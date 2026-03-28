package com.huah.ai.platform.gateway.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GatewayUsage {
    private int promptTokens;
    private int completionTokens;
}
