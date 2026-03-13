package com.huah.ai.platform.gateway.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChatResponse {
    private String content;
    private String model;
    private long latencyMs;
    private Integer promptTokens;
    private Integer completionTokens;
}
