package com.huah.ai.platform.rag.model;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class DocumentMetaResponse {
    Long id;
    String filename;
    Long knowledgeBaseId;
    Long fileSize;
    String storagePath;
    String contentType;
    int chunkCount;
    String uploadedBy;
    String status;
    String errorMessage;
    LocalDateTime createdAt;
    LocalDateTime indexedAt;
}
