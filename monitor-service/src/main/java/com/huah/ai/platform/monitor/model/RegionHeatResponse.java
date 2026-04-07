package com.huah.ai.platform.monitor.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RegionHeatResponse {
    String province;
    String city;
    String regionName;
    long calls;
    long errors;
    long avgLatencyMs;
    double successRate;
}
