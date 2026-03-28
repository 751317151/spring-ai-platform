package com.huah.ai.platform.rag.model;

import lombok.Data;

@Data
public class EvidenceFeedbackRequest {
    private Long responseId;
    private String chunkId;
    private Long knowledgeBaseId;
    private String feedback;
    private String comment;
}
