package com.huah.ai.platform.rag.model;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class RagEvaluationSample {
    String responseId;
    String userId;
    String knowledgeBaseId;
    String question;
    String answer;
    String feedback;
    String comment;
    long evidenceNegativeCount;
    LocalDateTime createdAt;
}
