package com.huah.ai.platform.agent.dto;

import lombok.Data;

@Data
public class ResponseFeedbackRequest {
    private Long responseId;
    private String feedback;
    private String comment;
}
