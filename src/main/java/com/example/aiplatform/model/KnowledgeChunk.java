package com.example.aiplatform.model;

import java.util.List;

public record KnowledgeChunk(
        String chunkId,
        String documentId,
        String businessDomain,
        DocumentType documentType,
        String content,
        List<Double> embedding
) {
}
