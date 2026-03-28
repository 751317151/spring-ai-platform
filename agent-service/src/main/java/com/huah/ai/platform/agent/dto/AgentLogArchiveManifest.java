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
public class AgentLogArchiveManifest {
    private String agentType;
    private String generatedAt;
    private boolean dryRun;
    private String bundleDir;
    private String manifestPath;
    private long exportedRecordCount;
    private AgentLogLifecycleSummaryResponse lifecycleSummary;
    private List<AgentLogArchiveArtifact> artifacts;
    private List<AgentLogArchiveSample> samples;
}
