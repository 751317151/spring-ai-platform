package com.huah.ai.platform.rag.model;

import lombok.Data;

@Data
public class EvidenceFeedbackRequest {
    private String responseId;
    private String chunkId;
    private String knowledgeBaseId;
    private String feedback;
    private String comment;
}
