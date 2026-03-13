package com.example.aiplatform.dto;

public record MonitoringSnapshot(
        long totalRequests,
        double errorRate,
        double averageLatencyMs,
        long totalTokensConsumed,
        long anomalyCount
) {
}
