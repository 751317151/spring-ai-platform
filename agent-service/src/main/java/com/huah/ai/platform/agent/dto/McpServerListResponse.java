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
public class McpServerListResponse {

    private boolean clientEnabled;

    private String source;

    private int count;

    private int issueCount;

    private String agentType;

    private int authorizedCount;

    private List<McpServerInfo> servers;
}
