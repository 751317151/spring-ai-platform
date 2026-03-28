package com.huah.ai.platform.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentLogArchiveManifestInfo {
    private String manifestDir;
    private String bundleDir;
    private String manifestPath;
    private String generatedAt;
    private long exportedRecordCount;
    private boolean enabled;
}
