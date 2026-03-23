package com.huah.ai.platform.monitor.model;

import com.huah.ai.platform.monitor.alert.AlertEventView;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class AlertsView {
    long activeAlerts;
    List<AlertEventView> alerts;
}
