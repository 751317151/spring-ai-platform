package com.huah.ai.platform.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentWorkbenchCompareInsight {
    private String type;
    private String severity;
    private String winnerAgentType;
    private String loserAgentType;
    private String metricKey;
    private String title;
    private String summary;
    private String leftEvidence;
    private String rightEvidence;
    private String whyItMatters;
    private String suggestedAction;
}
