package com.huah.ai.platform.monitor.alert;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertmanagerAlertService {

    private static final TypeReference<List<Map<String, Object>>> ALERT_LIST_TYPE = new TypeReference<>() {
    };

    private final AlertmanagerProperties properties;
    private final ObjectMapper objectMapper;

    public List<AlertEventResponse> fetchActiveAlerts() {
        if (!properties.isEnabled()) {
            return List.of();
        }

        try {
            RestClient client = RestClient.builder()
                    .baseUrl(properties.getBaseUrl())
                    .build();

            String body = client.get()
                    .uri(properties.getApiPath())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);

            if (body == null || body.isBlank()) {
                return List.of();
            }

            List<Map<String, Object>> payload = objectMapper.readValue(body, ALERT_LIST_TYPE);
            List<AlertEventResponse> alerts = new ArrayList<>();

            for (Map<String, Object> item : payload) {
                Object statusObject = item.get("status");
                if (!(statusObject instanceof Map<?, ?> statusMap)) {
                    continue;
                }
                String state = String.valueOf(statusMap.get("state"));
                if (!"active".equalsIgnoreCase(state)) {
                    continue;
                }

                Map<String, String> labels = stringifyMap(item.get("labels"));
                Map<String, String> annotations = stringifyMap(item.get("annotations"));

                alerts.add(AlertEventResponse.builder()
                        .level(normalizeSeverity(labels.get("severity")))
                        .type(stringValue(labels.get("alertname"), "Prometheus 告警"))
                        .message(stringValue(
                                annotations.get("description"),
                                stringValue(annotations.get("summary"), "外部告警系统触发了告警")
                        ))
                        .time(stringValue(item.get("startsAt"), OffsetDateTime.now().toString()))
                        .source("alertmanager")
                        .status(state)
                        .fingerprint(stringValue(item.get("fingerprint"), ""))
                        .silenceUrl(buildSilenceUrl(labels))
                        .labels(labels)
                        .build());
            }

            alerts.sort(Comparator.comparing(AlertEventResponse::getTime, (a, b) -> parseTime(b).compareTo(parseTime(a))));
            return alerts;
        } catch (Exception e) {
            log.warn("读取 Alertmanager 告警失败，将回退到本地规则评估: {}", e.getMessage());
            return List.of();
        }
    }

    private String buildSilenceUrl(Map<String, String> labels) {
        if (labels.isEmpty()) {
            return "";
        }
        String matcher = labels.entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=\"" + entry.getValue() + "\"")
                .collect(Collectors.joining(", ", "{", "}"));

        return properties.getUiBaseUrl() + properties.getSilencePath()
                + URLEncoder.encode(matcher, StandardCharsets.UTF_8);
    }

    private Map<String, String> stringifyMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        map.forEach((k, v) -> result.put(String.valueOf(k), String.valueOf(v)));
        return result;
    }

    private String normalizeSeverity(String severity) {
        String value = severity == null ? "" : severity.toLowerCase();
        return switch (value) {
            case "critical" -> "ERROR";
            case "warning" -> "WARNING";
            default -> "INFO";
        };
    }

    private String stringValue(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? defaultValue : text;
    }

    private OffsetDateTime parseTime(String value) {
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException e) {
            return OffsetDateTime.MIN;
        }
    }
}
