package com.huah.ai.platform.rag.model;

import lombok.Data;

@Data
public class ResponseFeedbackRequest {
    private Long responseId;
    private String feedback;
    private String comment;
}
