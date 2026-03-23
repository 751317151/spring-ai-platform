package com.huah.ai.platform.monitor.alert;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

@Value
@Builder
public class AlertEventView {
    String level;
    String type;
    String message;
    String time;
    String source;
    String status;
    String fingerprint;
    String silenceUrl;
    Map<String, String> labels;
}
