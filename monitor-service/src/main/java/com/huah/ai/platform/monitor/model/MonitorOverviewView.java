package com.huah.ai.platform.monitor.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MonitorOverviewView {
    long totalRequests;
    long errorRequests;
    double successRate;
    long avgLatencyMs;
    long p95LatencyMs;
    long p99LatencyMs;
    long totalPromptTokens;
    long totalCompletionTokens;
    long totalTokens;
    double activeRequests;
}
