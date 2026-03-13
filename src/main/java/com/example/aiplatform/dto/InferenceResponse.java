package com.example.aiplatform.dto;

public record InferenceResponse(
        String modelId,
        String response,
        boolean degraded,
        long latencyMs
) {
}
