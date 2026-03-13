package com.example.aiplatform.model;

import java.time.Instant;

public record AgentMemory(
        String userId,
        String shortTermSummary,
        String longTermProfile,
        Instant updatedAt
) {
}
