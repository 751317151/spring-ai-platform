package com.huah.ai.platform.agent.controller;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AgentRequestContext {
    String userId;
    String roles;
    String department;
}
