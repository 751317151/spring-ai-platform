package com.example.aiplatform.dto;

import com.example.aiplatform.model.DocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RagIngestRequest(
        @NotBlank String documentId,
        @NotNull DocumentType documentType,
        @NotBlank String content,
        String businessDomain
) {
}
