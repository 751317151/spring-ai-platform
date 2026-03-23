package com.huah.ai.platform.monitor.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TokenUsageView {
    String userId;
    String date;
    long tokensUsed;
}
