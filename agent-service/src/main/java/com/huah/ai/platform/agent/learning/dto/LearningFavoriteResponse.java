package com.huah.ai.platform.agent.learning.dto;

import com.huah.ai.platform.agent.dto.SessionConfigRequest;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class LearningFavoriteResponse {
    private Long id;
    private Long responseId;
    private String role;
    private String content;
    private String agentType;
    private String sessionId;
    private String sessionSummary;
    private Integer sourceMessageIndex;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastCollectedAt;
    private Integer duplicateCount;
    private List<String> tags;
    private SessionConfigRequest sessionConfigSnapshot;
}
