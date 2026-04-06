package com.huah.ai.platform.monitor.model;

import com.huah.ai.platform.monitor.alert.AlertEventResponse;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class AlertsResponse {
    long activeAlerts;
    List<AlertEventResponse> alerts;
}

