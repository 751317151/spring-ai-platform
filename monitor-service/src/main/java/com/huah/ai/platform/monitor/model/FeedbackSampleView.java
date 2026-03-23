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
public class FeedbackSampleView {
    private String responseId;
    private String userId;
    private String sourceType;
    private String agentType;
    private String knowledgeBaseId;
    private String feedback;
    private String comment;
    private LocalDateTime createdAt;
}
