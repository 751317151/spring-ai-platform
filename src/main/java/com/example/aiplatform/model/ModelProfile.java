package com.example.aiplatform.model;

import java.time.Instant;
import java.util.Map;

public record ModelProfile(
        String modelId,
        String displayName,
        ModelProviderType providerType,
        String endpoint,
        String apiKey,
        Map<String, String> metadata,
        Instant registeredAt,
        boolean healthy
) {
}
