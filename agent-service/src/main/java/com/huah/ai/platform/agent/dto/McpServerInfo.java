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
public class McpServerInfo {

    private String code;

    private String command;

    private List<String> args;

    private boolean enabled;

    private boolean clientEnabled;

    private String source;
}
