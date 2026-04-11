package com.huah.ai.platform.agent.service;

import com.huah.ai.platform.agent.entity.AgentDefinitionEntity;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class AssistantAgentRegistry {

    private final Map<String, AssistantAgent> executorAgentsByProfile;
    private final AgentDefinitionService agentDefinitionService;
    private final DynamicAssistantAgent dynamicAssistantAgent;

    public AssistantAgentRegistry(List<AssistantAgent> agents,
                                  AgentDefinitionService agentDefinitionService,
                                  DynamicAssistantAgent dynamicAssistantAgent) {
        Map<String, AssistantAgent> mapping = new LinkedHashMap<>();
        for (AssistantAgent agent : agents) {
            AssistantAgent previous = mapping.put(agent.getAgentType(), agent);
            if (previous != null) {
                throw new IllegalStateException("Duplicate assistant agent type: " + agent.getAgentType());
            }
        }
        this.executorAgentsByProfile = Map.copyOf(mapping);
        this.agentDefinitionService = agentDefinitionService;
        this.dynamicAssistantAgent = dynamicAssistantAgent;
    }

    public AssistantAgent getRequired(String agentType) {
        Optional<AgentDefinitionEntity> definition = agentDefinitionService.findEnabledEntity(agentType);
        if (definition.isPresent()) {
            return bindRegisteredAgent(definition.get());
        }
        AssistantAgent fallback = executorAgentsByProfile.get(agentType);
        if (fallback != null) {
            return fallback;
        }
        throw new IllegalArgumentException("Unknown agent: " + agentType);
    }

    public boolean supports(String agentType) {
        return agentDefinitionService.findEnabledEntity(agentType).isPresent() || executorAgentsByProfile.containsKey(agentType);
    }

    public Optional<AgentDefinitionEntity> findEnabledDefinition(String agentType) {
        return agentDefinitionService.findEnabledEntity(agentType);
    }

    public Set<String> getRegisteredAgentTypes() {
        Set<String> registered = new LinkedHashSet<>(executorAgentsByProfile.keySet());
        for (AgentDefinitionEntity definition : agentDefinitionService.listEnabledEntities()) {
            registered.add(definition.getAgentCode());
        }
        return Set.copyOf(registered);
    }

    private AssistantAgent bindRegisteredAgent(AgentDefinitionEntity definition) {
        AssistantAgent executor = executorAgentsByProfile.get(definition.getAssistantProfile());
        if (executor == null) {
            return new DynamicRegisteredAssistantAgent(definition.getAgentCode(), dynamicAssistantAgent);
        }
        return new ProfileBoundAssistantAgent(definition.getAgentCode(), executor);
    }

    private record ProfileBoundAssistantAgent(String agentType, AssistantAgent executor) implements AssistantAgent {

        @Override
        public String getAgentType() {
            return agentType;
        }

        @Override
        public Flux<ChatResponse> chatStream(String userId, String sessionId, String message) {
            if (executor instanceof BaseAssistantAgent baseAssistantAgent) {
                return baseAssistantAgent.chatStreamAs(agentType, userId, sessionId, message);
            }
            return executor.chatStream(userId, sessionId, message);
        }
    }

    private record DynamicRegisteredAssistantAgent(String agentType,
                                                   DynamicAssistantAgent dynamicAssistantAgent)
            implements AssistantAgent {

        @Override
        public String getAgentType() {
            return agentType;
        }

        @Override
        public Flux<ChatResponse> chatStream(String userId, String sessionId, String message) {
            return dynamicAssistantAgent.chatStream(agentType, userId, sessionId, message);
        }
    }
}
