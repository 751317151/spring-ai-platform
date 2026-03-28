package com.huah.ai.platform.agent.learning.dto;

import lombok.Data;

import java.util.List;

@Data
public class LearningNotePayload {
    private Long id;
    private String title;
    private String content;
    private String sourceType;
    private Long relatedFavoriteId;
    private String relatedSessionId;
    private String relatedAgentType;
    private String relatedSessionSummary;
    private Integer relatedMessageIndex;
    private List<String> tags;
    private Long createdAt;
    private Long updatedAt;
}
