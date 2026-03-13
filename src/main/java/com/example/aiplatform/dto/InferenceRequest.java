package com.example.aiplatform.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record InferenceRequest(
        @NotBlank String prompt,
        String preferredModel,
        Map<String, Object> context,
        boolean fallbackEnabled
) {
}
