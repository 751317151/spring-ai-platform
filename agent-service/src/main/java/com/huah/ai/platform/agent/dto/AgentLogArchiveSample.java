package com.huah.ai.platform.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentLogArchiveSample {
    private String type;
    private String id;
    private String traceId;
    private String sessionId;
    private String summary;
    private String createdAt;
}
