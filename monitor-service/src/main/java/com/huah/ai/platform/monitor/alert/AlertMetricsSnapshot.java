package com.huah.ai.platform.monitor.alert;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AlertMetricsSnapshot {
    long totalCount;
    long errorCount;
    double errorRate;
    double p95LatencyMs;
    double tokenLimitExceeded;
    double activeRequests;
}
