package com.huah.ai.platform.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentWorkbenchCompareChangeItem {
    private String type;
    private String label;
    private String leftSummary;
    private String rightSummary;
    private String direction;
    private String severity;
    private String suggestedAction;
}
