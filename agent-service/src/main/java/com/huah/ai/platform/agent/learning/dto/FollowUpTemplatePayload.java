package com.huah.ai.platform.agent.learning.dto;

import lombok.Data;

@Data
public class FollowUpTemplatePayload {
    private Long id;
    private String name;
    private String content;
    private Integer sourceCount;
    private Long updatedAt;
}
