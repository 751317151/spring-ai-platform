package com.example.aiplatform.dto;

import com.example.aiplatform.model.ModelProviderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record ModelRegistrationRequest(
        @NotBlank String modelId,
        @NotBlank String displayName,
        @NotNull ModelProviderType providerType,
        @NotBlank String endpoint,
        String apiKey,
        Map<String, String> metadata
) {
}
