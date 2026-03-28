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
public class AgentLogArchiveDetailResponse {
    private String agentType;
    private boolean enabled;
    private String manifestDir;
    private String bundleDir;
    private String manifestPath;
    private String generatedAt;
    private boolean dryRun;
    private long exportedRecordCount;
    private long coldDataCount;
    private int sampleLimit;
    private int exportBatchSize;
    private List<String> operationHints;
    private List<AgentLogArchiveArtifact> artifacts;
    private List<AgentLogArchiveSample> samples;
}
