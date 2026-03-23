package com.huah.ai.platform.monitor.alert;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AlertEvaluationService {

    private final AlertRuleProperties ruleProperties;
    private final MeterRegistry meterRegistry;

    public List<AlertEventView> evaluate(AlertMetricsSnapshot snapshot) {
        List<AlertEventView> alerts = new ArrayList<>();

        for (AlertRuleProperties.Rule rule : ruleProperties.getRules()) {
            Optional<String> message = evaluateRule(rule, snapshot);
            message.ifPresent(value -> alerts.add(AlertEventView.builder()
                    .level(rule.getLevel())
                    .type(rule.getType())
                    .message(value)
                    .time(LocalDateTime.now().toString())
                    .source("local-rule")
                    .status("active")
                    .fingerprint(rule.getId())
                    .labels(Map.of("ruleId", rule.getId(), "metric", rule.getMetric()))
                    .build()));
        }

        if (alerts.isEmpty()) {
            alerts.add(AlertEventView.builder()
                    .level("INFO")
                    .type("系统正常")
                    .message(snapshot.getTotalCount() > 0
                            ? "当前未触发规则告警，系统运行正常"
                            : "当前暂无可评估的请求数据")
                    .time(LocalDateTime.now().toString())
                    .source("local-rule")
                    .status("resolved")
                    .fingerprint("system-ok")
                    .labels(Map.of())
                    .build());
        }

        return alerts;
    }

    private Optional<String> evaluateRule(AlertRuleProperties.Rule rule, AlertMetricsSnapshot snapshot) {
        return switch (rule.getMetric()) {
            case "errorRate" -> snapshot.getTotalCount() > 0 && snapshot.getErrorRate() > rule.getThreshold()
                    ? Optional.of(String.format("%s，当前错误率 %.1f%%", rule.getSummary(), snapshot.getErrorRate() * 100))
                    : Optional.empty();
            case "errorCount" -> snapshot.getErrorCount() > rule.getThreshold()
                    ? Optional.of(String.format("%s，当前错误请求 %d 次", rule.getSummary(), snapshot.getErrorCount()))
                    : Optional.empty();
            case "p95LatencyMs" -> snapshot.getP95LatencyMs() > rule.getThreshold()
                    ? Optional.of(String.format("%s，当前 P95 延迟 %.0f ms", rule.getSummary(), snapshot.getP95LatencyMs()))
                    : Optional.empty();
            case "tokenLimitExceeded" -> snapshot.getTokenLimitExceeded() > rule.getThreshold()
                    ? Optional.of(String.format("%s，当前已拦截 %.0f 次", rule.getSummary(), snapshot.getTokenLimitExceeded()))
                    : Optional.empty();
            case "activeRequests" -> snapshot.getActiveRequests() > rule.getThreshold()
                    ? Optional.of(String.format("%s，当前活跃请求 %.0f", rule.getSummary(), snapshot.getActiveRequests()))
                    : Optional.empty();
            case "dependencyFailures" -> dependencyFailures() > rule.getThreshold()
                    ? Optional.of(String.format("%s，当前累计失败 %.0f 次", rule.getSummary(), dependencyFailures()))
                    : Optional.empty();
            default -> Optional.empty();
        };
    }

    private double dependencyFailures() {
        return meterRegistry.find("rag.dependency.failures").counters().stream().mapToDouble(c -> c.count()).sum()
                + meterRegistry.find("ai.dependency.failures").counters().stream().mapToDouble(c -> c.count()).sum()
                + meterRegistry.find("gateway.dependency.failures").counters().stream().mapToDouble(c -> c.count()).sum();
    }
}
