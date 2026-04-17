package com.huah.ai.platform.agent.service;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

import com.huah.ai.platform.agent.audit.ToolExecutionContext;
import com.huah.ai.platform.agent.config.AgentChatClientFactory;
import com.huah.ai.platform.agent.dto.SessionConfigResponse;
import com.huah.ai.platform.agent.entity.AgentDefinitionEntity;
import com.huah.ai.platform.agent.memory.ConversationMemoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@Slf4j
public class DynamicAssistantAgent {

    private final AgentDefinitionService agentDefinitionService;
    private final AgentChatClientFactory chatClientFactory;
    private final ChatModel chatModel;
    private final ChatMemory chatMemory;
    private final ConversationMemoryService conversationMemoryService;
    private final SessionRuntimeInstructionBuilder sessionRuntimeInstructionBuilder;
    private final AgentModelSupportService agentModelSupportService;
    private final DynamicAgentCapabilityCacheService dynamicAgentCapabilityCacheService;

    public DynamicAssistantAgent(AgentDefinitionService agentDefinitionService,
                                 AgentChatClientFactory chatClientFactory,
                                 ChatModel chatModel,
                                 ChatMemory chatMemory,
                                 ConversationMemoryService conversationMemoryService,
                                 SessionRuntimeInstructionBuilder sessionRuntimeInstructionBuilder,
                                 AgentModelSupportService agentModelSupportService,
                                 DynamicAgentCapabilityCacheService dynamicAgentCapabilityCacheService) {
        this.agentDefinitionService = agentDefinitionService;
        this.chatClientFactory = chatClientFactory;
        this.chatModel = chatModel;
        this.chatMemory = chatMemory;
        this.conversationMemoryService = conversationMemoryService;
        this.sessionRuntimeInstructionBuilder = sessionRuntimeInstructionBuilder;
        this.agentModelSupportService = agentModelSupportService;
        this.dynamicAgentCapabilityCacheService = dynamicAgentCapabilityCacheService;
    }

    public Flux<ChatResponse> chatStream(String agentType, String userId, String sessionId, String message) {
        long preparationStart = System.currentTimeMillis();
        AgentDefinitionEntity definition = agentDefinitionService.getRequiredEnabledEntity(agentType);
        SessionConfigResponse sessionConfig = conversationMemoryService.getSessionConfig(sessionId);
        String runtimeInstruction = sessionRuntimeInstructionBuilder.build(sessionConfig);
        String systemPrompt = renderSystemPrompt(definition, userId);
        ChatOptions chatOptions = buildChatOptions(sessionConfig, definition);
        String enrichedMessage = enrichMessage(message, runtimeInstruction);
        ChatClient chatClient = buildChatClient(definition);
        long preparationLatency = System.currentTimeMillis() - preparationStart;
        ToolExecutionContext.set(userId, sessionId, agentType);
        AgentExecutionMetricsContext.set(new AgentExecutionMetrics(preparationLatency, 0));
        return chatClient
                .prompt()
                .system(systemPrompt)
                .options(chatOptions)
                .user(enrichedMessage)
                .advisors(a -> a.param(CONVERSATION_ID, sessionId))
                .stream()
                .chatResponse()
                .doFinally(signalType -> ToolExecutionContext.clear());
    }

    ChatClient buildChatClient(AgentDefinitionEntity definition) {
        DynamicAgentCapabilityCacheService.DynamicAgentCapabilities capabilities =
                dynamicAgentCapabilityCacheService.getCapabilities(definition.getAgentCode());
        return chatClientFactory.buildDynamicChatClient(
                chatModel,
                chatMemory,
                "",
                capabilities.toolCallbackProvider(),
                capabilities.toolArray());
    }

    ChatOptions buildChatOptions(SessionConfigResponse config, AgentDefinitionEntity definition) {
        ChatOptions.Builder builder = ChatOptions.builder();
        String requestedModel = config != null ? normalizeNullable(config.getModel()) : null;
        if (requestedModel != null && !"auto".equalsIgnoreCase(requestedModel)) {
            builder.model(requestedModel);
        } else {
            String definitionModel = normalizeNullable(definition.getDefaultModel());
            if (agentModelSupportService.shouldApplyExplicitModel(definitionModel)) {
                builder.model(definitionModel);
            } else if (definitionModel != null
                    && !"auto".equalsIgnoreCase(definitionModel)) {
                log.warn("Skip unsupported assistant default model and fallback to auto: agentCode={}, defaultModel={}",
                        definition.getAgentCode(), definitionModel);
            }
        }
        if (config != null && config.getTemperature() != null) {
            builder.temperature(config.getTemperature());
        }
        return builder.build();
    }

    private String enrichMessage(String message, String runtimeInstruction) {
        if (runtimeInstruction == null || runtimeInstruction.isBlank()) {
            return message;
        }
        return runtimeInstruction + "\n\n[user-question]\n" + message;
    }

    String renderSystemPrompt(AgentDefinitionEntity definition, String userId) {
        String resolvedUserId = normalizeNullable(userId);
        String safeUserId = resolvedUserId == null ? "unknown" : resolvedUserId;
        String resolvedInstruction = definition.getSystemPrompt().replace("{userId}", safeUserId);
        StringBuilder builder = new StringBuilder();
        builder.append("You are a custom assistant type in the enterprise AI platform named \"")
                .append(definition.getAgentName())
                .append("\".\n");
        if (definition.getDescription() != null && !definition.getDescription().isBlank()) {
            builder.append("Assistant description: ")
                    .append(definition.getDescription().trim())
                    .append("\n");
        }
        builder.append("Follow these system instructions strictly:\n")
                .append(resolvedInstruction.trim())
                .append("\nCurrent user: ")
                .append(safeUserId);
        return builder.toString();
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
