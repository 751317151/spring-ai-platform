package com.example.aiplatform.dto;

import jakarta.validation.constraints.NotBlank;

public record RagQueryRequest(
        @NotBlank String question,
        String businessDomain,
        int topK
) {
}
