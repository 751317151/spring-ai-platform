package com.huah.ai.platform.agent.service;

import com.huah.ai.platform.agent.dto.AgentDefinitionResponse;
import com.huah.ai.platform.agent.dto.AgentMetadataItem;
import com.huah.ai.platform.agent.dto.AgentMetadataResponse;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AgentMetadataService {

    private static final String DEFAULT_MODEL = "auto";
    private static final double DEFAULT_TEMPERATURE = 0.7d;
    private static final int DEFAULT_MAX_CONTEXT_MESSAGES = 10;

    private final AssistantAgentRegistry assistantAgentRegistry;
    private final AgentDefinitionService agentDefinitionService;
    private final AssistantProfileCatalog assistantProfileCatalog;

    public AgentMetadataService(
            AssistantAgentRegistry assistantAgentRegistry,
            AgentDefinitionService agentDefinitionService,
            AssistantProfileCatalog assistantProfileCatalog) {
        this.assistantAgentRegistry = assistantAgentRegistry;
        this.agentDefinitionService = agentDefinitionService;
        this.assistantProfileCatalog = assistantProfileCatalog;
    }

    public AgentMetadataResponse list() {
        List<AgentMetadataItem> agents = new ArrayList<>();
        for (AgentDefinitionResponse definition : agentDefinitionService.listEnabled()) {
            AssistantProfileCatalog.AssistantProfileDescriptor profile =
                    assistantProfileCatalog.getRequiredProfile(definition.getAssistantProfile());
            agents.add(AgentMetadataItem.builder()
                    .agentType(definition.getAgentCode())
                    .name(definition.getAgentName())
                    .icon(definition.getIcon())
                    .color(definition.getColor())
                    .description(definition.getDescription() == null ? "" : definition.getDescription())
                    .defaultModel(definition.getDefaultModel() == null ? DEFAULT_MODEL : definition.getDefaultModel())
                    .defaultTemperature(DEFAULT_TEMPERATURE)
                    .defaultMaxContextMessages(DEFAULT_MAX_CONTEXT_MESSAGES)
                    .supportsKnowledge(profile.supportsKnowledge())
                    .supportsTools(profile.supportsTools())
                    .supportsMultiAgentMode(profile.supportsMultiAgentMode())
                    .supportsMultiStepRecovery(profile.supportsMultiStepRecovery())
                    .registered(assistantAgentRegistry.supports(definition.getAgentCode()))
                    .build());
        }
        agents.add(metadata("multi", "多智能体", "MA", "#f97316", "复杂任务拆分、多助手协同和结果汇总", true, true, true, false, true));
        return AgentMetadataResponse.builder()
                .count(agents.size())
                .agents(agents)
                .build();
    }

    private AgentMetadataItem metadata(
            String agentType,
            String name,
            String icon,
            String color,
            String description,
            boolean supportsKnowledge,
            boolean supportsTools,
            boolean supportsMultiAgentMode,
            boolean supportsMultiStepRecovery,
            boolean registered) {
        return AgentMetadataItem.builder()
                .agentType(agentType)
                .name(name)
                .icon(icon)
                .color(color)
                .description(description)
                .defaultModel(DEFAULT_MODEL)
                .defaultTemperature(DEFAULT_TEMPERATURE)
                .defaultMaxContextMessages(DEFAULT_MAX_CONTEXT_MESSAGES)
                .supportsKnowledge(supportsKnowledge)
                .supportsTools(supportsTools)
                .supportsMultiAgentMode(supportsMultiAgentMode)
                .supportsMultiStepRecovery(supportsMultiStepRecovery)
                .registered(registered)
                .build();
    }
}
