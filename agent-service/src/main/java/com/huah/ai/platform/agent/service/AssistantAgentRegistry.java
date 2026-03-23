package com.huah.ai.platform.agent.service;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class AssistantAgentRegistry {

    private final Map<String, AssistantAgent> agentsByType;

    public AssistantAgentRegistry(List<AssistantAgent> agents) {
        Map<String, AssistantAgent> mapping = new LinkedHashMap<>();
        for (AssistantAgent agent : agents) {
            AssistantAgent previous = mapping.put(agent.getAgentType(), agent);
            if (previous != null) {
                throw new IllegalStateException("Duplicate assistant agent type: " + agent.getAgentType());
            }
        }
        this.agentsByType = Map.copyOf(mapping);
    }

    public AssistantAgent getRequired(String agentType) {
        AssistantAgent agent = agentsByType.get(agentType);
        if (agent == null) {
            throw new IllegalArgumentException("Unknown agent: " + agentType);
        }
        return agent;
    }

    public boolean supports(String agentType) {
        return agentsByType.containsKey(agentType);
    }

    public Set<String> getRegisteredAgentTypes() {
        return agentsByType.keySet();
    }
}
