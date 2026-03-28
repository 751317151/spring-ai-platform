package com.huah.ai.platform.agent.learning.dto;

import com.huah.ai.platform.agent.dto.SessionConfigRequest;
import lombok.Data;

import java.util.List;

@Data
public class LearningFavoritePayload {
    private String id;
    private String responseId;
    private String role;
    private String content;
    private String agentType;
    private String sessionId;
    private String sessionSummary;
    private Integer sourceMessageIndex;
    private Long createdAt;
    private Long lastCollectedAt;
    private Integer duplicateCount;
    private List<String> tags;
    private SessionConfigRequest sessionConfigSnapshot;
}
