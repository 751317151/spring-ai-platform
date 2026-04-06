package com.huah.ai.platform.monitor.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvidenceFeedbackSampleResponse {
    private String responseId;
    private String chunkId;
    private String userId;
    private String knowledgeBaseId;
    private String feedback;
    private String comment;
    private LocalDateTime createdAt;
}

