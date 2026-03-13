package com.example.aiplatform.dto;

import com.example.aiplatform.model.AgentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record AgentTaskRequest(
        @NotNull AgentType agentType,
        @NotBlank String userId,
        @NotBlank String task,
        Map<String, Object> context
) {
}
