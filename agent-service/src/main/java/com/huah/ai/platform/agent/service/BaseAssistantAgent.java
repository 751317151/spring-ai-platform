package com.huah.ai.platform.agent.service;

import com.huah.ai.platform.agent.audit.ToolExecutionContext;
import com.huah.ai.platform.agent.dto.SessionConfigResponse;
import com.huah.ai.platform.agent.memory.ConversationMemoryService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import reactor.core.publisher.Flux;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

public abstract class BaseAssistantAgent implements AssistantAgent {

    private final String agentType;
    private final ChatClient chatClient;
    private final ConversationMemoryService conversationMemoryService;
    private final SessionRuntimeInstructionBuilder sessionRuntimeInstructionBuilder;

    protected BaseAssistantAgent(String agentType,
                                 ChatClient chatClient,
                                 ConversationMemoryService conversationMemoryService,
                                 SessionRuntimeInstructionBuilder sessionRuntimeInstructionBuilder) {
        this.agentType = agentType;
        this.chatClient = chatClient;
        this.conversationMemoryService = conversationMemoryService;
        this.sessionRuntimeInstructionBuilder = sessionRuntimeInstructionBuilder;
    }

    @Override
    public String getAgentType() {
        return agentType;
    }

    @Override
    public AgentChatResult chat(String userId, String sessionId, String message) {
        return chatAs(agentType, userId, sessionId, message);
    }

    public AgentChatResult chatAs(String effectiveAgentType, String userId, String sessionId, String message) {
        long preparationStart = System.currentTimeMillis();
        SessionConfigResponse sessionConfig = conversationMemoryService.getSessionConfig(sessionId);
        String runtimeInstruction = sessionRuntimeInstructionBuilder.build(sessionConfig);
        ChatOptions chatOptions = buildChatOptions(sessionConfig);
        String enrichedMessage = enrichMessage(message, runtimeInstruction);
        long preparationLatency = System.currentTimeMillis() - preparationStart;
        ToolExecutionContext.set(userId, sessionId, effectiveAgentType);
        try {
            long modelStart = System.currentTimeMillis();
            ChatResponse chatResponse = chatClient
                    .prompt()
                    .system(s -> s.param("userId", userId))
                    .options(chatOptions)
                    .user(enrichedMessage)
                    .advisors(a -> a.param(CONVERSATION_ID, sessionId))
                    .call()
                    .chatResponse();
            long modelLatency = System.currentTimeMillis() - modelStart;
            return AgentChatResult.fromChatResponse(chatResponse)
                    .withExecutionMetrics(new AgentExecutionMetrics(preparationLatency, modelLatency));
        } finally {
            ToolExecutionContext.clear();
        }
    }

    @Override
    public Flux<ChatResponse> chatStream(String userId, String sessionId, String message) {
        return chatStreamAs(agentType, userId, sessionId, message);
    }

    public Flux<ChatResponse> chatStreamAs(String effectiveAgentType, String userId, String sessionId, String message) {
        long preparationStart = System.currentTimeMillis();
        SessionConfigResponse sessionConfig = conversationMemoryService.getSessionConfig(sessionId);
        String runtimeInstruction = sessionRuntimeInstructionBuilder.build(sessionConfig);
        ChatOptions chatOptions = buildChatOptions(sessionConfig);
        String enrichedMessage = enrichMessage(message, null);
        long preparationLatency = System.currentTimeMillis() - preparationStart;
        AgentExecutionMetricsContext.set(new AgentExecutionMetrics(preparationLatency, 0));
        ToolExecutionContext.set(userId, sessionId, effectiveAgentType);
        return chatClient
                .prompt()
                .system(s -> s.param("userId", userId))
                .options(chatOptions)
                .user(enrichedMessage)
                .advisors(a -> a.param(CONVERSATION_ID, sessionId))
                .stream()
                .chatResponse()
                .doFinally(signalType -> ToolExecutionContext.clear());
    }

    private ChatOptions buildChatOptions(SessionConfigResponse config) {
        ChatOptions.Builder builder = ChatOptions.builder();
        if (config != null) {
            if (config.getModel() != null && !config.getModel().isBlank() && !"auto".equalsIgnoreCase(config.getModel())) {
                builder.model(config.getModel().trim());
            }
            if (config.getTemperature() != null) {
                builder.temperature(config.getTemperature());
            }
        }
        return builder.build();
    }

    private String enrichMessage(String message, String runtimeInstruction) {
        if (runtimeInstruction == null || runtimeInstruction.isBlank()) {
            return message;
        }
        return runtimeInstruction + "\n\n[用户问题]\n" + message;
    }
}
