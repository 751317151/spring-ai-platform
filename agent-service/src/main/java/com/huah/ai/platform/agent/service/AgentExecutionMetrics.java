package com.huah.ai.platform.agent.service;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AgentExecutionMetrics {

    private final long preparationLatencyMs;
    private final long modelLatencyMs;
}
