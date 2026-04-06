package com.huah.ai.platform.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeBaseRequest {

    private String name;
    private String description;
    private String department;
    private Integer chunkSize;
    private Integer chunkOverlap;
    private String chunkStrategy;
    private Integer structuredBatchSize;
    private String createdBy;
    private String visibilityScope;
    private String status;
}
