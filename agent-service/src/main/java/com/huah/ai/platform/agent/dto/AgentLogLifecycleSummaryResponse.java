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
public class AgentLogLifecycleSummaryResponse {
    private String agentType;
    private long totalActiveCount;
    private long totalArchiveCandidateCount;
    private long totalDeleteCandidateCount;
    private long totalColdDataCount;
    private boolean automationEnabled;
    private boolean automationDryRun;
    private long automationIntervalMs;
    private String archiveManifestDir;
    private String lastArchiveBundleDir;
    private String lastArchiveManifestPath;
    private String lastArchiveManifestAt;
    private long lastArchiveExportedRecordCount;
    private List<AgentLogLifecycleBucket> buckets;
    private String summary;
}
