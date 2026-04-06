package com.huah.ai.platform.agent.learning.dto;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class FollowUpTemplateResponse {
    private Long id;
    private String name;
    private String content;
    private Integer sourceCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
