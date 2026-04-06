package com.huah.ai.platform.monitor.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class HourlyStatResponse {
    int hour;
    long total;
    long errors;
    double avg_latency;
    double p50;
    double p95;
}

