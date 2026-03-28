package com.huah.ai.platform.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentLogLifecycleBucket {
    private String type;
    private int archiveAfterDays;
    private int deleteAfterDays;
    private long activeCount;
    private long archiveCandidateCount;
    private long deleteCandidateCount;
}
