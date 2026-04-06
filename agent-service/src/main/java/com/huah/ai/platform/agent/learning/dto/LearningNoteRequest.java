package com.huah.ai.platform.agent.learning.dto;

import java.util.List;
import lombok.Data;

@Data
public class LearningNoteRequest {
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
}
