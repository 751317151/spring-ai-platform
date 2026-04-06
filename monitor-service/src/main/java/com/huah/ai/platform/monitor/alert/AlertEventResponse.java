package com.huah.ai.platform.monitor.alert;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.Map;

@Value
@Builder
public class AlertEventResponse {
    String level;
    String type;
    String message;
    String time;
    String source;
    String status;
    String fingerprint;
    String silenceUrl;
    String workflowStatus;
    String workflowNote;
    LocalDateTime workflowUpdatedAt;
    LocalDateTime silencedUntil;
    Map<String, String> labels;
}

