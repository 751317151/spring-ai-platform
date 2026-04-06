package com.huah.ai.platform.rag.model;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class KnowledgeBaseResponse {
    Long id;
    String name;
    String description;
    String department;
    int chunkSize;
    int chunkOverlap;
    String chunkStrategy;
    int structuredBatchSize;
    String createdBy;
    String visibilityScope;
    LocalDateTime createdAt;
    String status;
    int documentCount;
    int totalChunks;
}
