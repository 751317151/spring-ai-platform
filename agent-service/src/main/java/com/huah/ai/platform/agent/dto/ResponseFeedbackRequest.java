package com.huah.ai.platform.agent.dto;

import lombok.Data;

@Data
public class ResponseFeedbackRequest {
    private String responseId;
    private String feedback;
    private String comment;
}
