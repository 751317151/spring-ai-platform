package com.huah.ai.platform.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentLogArchivePreviewResponse {
    private String agentType;
    private String artifactType;
    private String bundleDir;
    private String artifactPath;
    private int previewLimit;
    private List<AgentLogArchivePreviewItem> items;
}
