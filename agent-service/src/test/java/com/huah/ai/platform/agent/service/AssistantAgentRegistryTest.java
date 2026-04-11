package com.huah.ai.platform.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huah.ai.platform.agent.entity.AgentDefinitionEntity;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

class AssistantAgentRegistryTest {

    @Test
    void shouldResolveRegisteredAgentByType() {
        StubAssistantAgent rdAgent = new StubAssistantAgent("rd");
        StubAssistantAgent codeAgent = new StubAssistantAgent("code");
        AgentDefinitionService agentDefinitionService = Mockito.mock(AgentDefinitionService.class);
        DynamicAssistantAgent dynamicAssistantAgent = Mockito.mock(DynamicAssistantAgent.class);
        Mockito.when(agentDefinitionService.findEnabledEntity("rd")).thenReturn(Optional.empty());
        Mockito.when(agentDefinitionService.findEnabledEntity("code")).thenReturn(Optional.empty());
        Mockito.when(agentDefinitionService.findEnabledEntity("mcp")).thenReturn(Optional.empty());
        Mockito.when(agentDefinitionService.listEnabledEntities()).thenReturn(List.of());
        AssistantAgentRegistry registry = new AssistantAgentRegistry(
                List.of(rdAgent, codeAgent),
                agentDefinitionService,
                dynamicAssistantAgent);

        assertTrue(registry.supports("rd"));
        assertTrue(registry.supports("code"));
        assertFalse(registry.supports("mcp"));
        assertEquals(rdAgent, registry.getRequired("rd"));
        assertEquals(codeAgent, registry.getRequired("code"));
    }

    @Test
    void shouldSupportProfileBoundDynamicAgentType() {
        AgentDefinitionService agentDefinitionService = Mockito.mock(AgentDefinitionService.class);
        DynamicAssistantAgent dynamicAssistantAgent = Mockito.mock(DynamicAssistantAgent.class);
        AgentDefinitionEntity definition = AgentDefinitionEntity.builder()
                .id(3001L)
                .agentCode("legal-agent")
                .agentName("法务助手")
                .assistantProfile("generic")
                .systemPrompt("你是法务助手")
                .enabled(true)
                .build();
        Mockito.when(agentDefinitionService.findEnabledEntity("legal-agent")).thenReturn(Optional.of(definition));
        Mockito.when(agentDefinitionService.listEnabledEntities()).thenReturn(List.of(definition));
        AssistantAgentRegistry registry = new AssistantAgentRegistry(
                List.of(new StubAssistantAgent("rd")),
                agentDefinitionService,
                dynamicAssistantAgent);

        assertTrue(registry.supports("legal-agent"));
        assertEquals("legal-agent", registry.getRequired("legal-agent").getAgentType());
    }

    @Test
    void shouldFallbackToDynamicExecutorForGenericProfile() {
        AgentDefinitionService agentDefinitionService = Mockito.mock(AgentDefinitionService.class);
        DynamicAssistantAgent dynamicAssistantAgent = Mockito.mock(DynamicAssistantAgent.class);
        AgentDefinitionEntity definition = AgentDefinitionEntity.builder()
                .id(3002L)
                .agentCode("ops-risk")
                .agentName("运营风控助手")
                .assistantProfile("generic")
                .systemPrompt("你是运营风控助手")
                .enabled(true)
                .build();
        Mockito.when(agentDefinitionService.findEnabledEntity("ops-risk")).thenReturn(Optional.of(definition));
        Mockito.when(agentDefinitionService.listEnabledEntities()).thenReturn(List.of(definition));
        AssistantAgentRegistry registry = new AssistantAgentRegistry(
                List.of(new StubAssistantAgent("rd")),
                agentDefinitionService,
                dynamicAssistantAgent);

        assertEquals("ops-risk", registry.getRequired("ops-risk").getAgentType());
    }

    @Test
    void shouldRejectDuplicateAgentType() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> new AssistantAgentRegistry(
                        List.of(new StubAssistantAgent("rd"), new StubAssistantAgent("rd")),
                        Mockito.mock(AgentDefinitionService.class),
                        Mockito.mock(DynamicAssistantAgent.class)));

        assertTrue(exception.getMessage().contains("Duplicate assistant agent type"));
    }

    @Test
    void shouldRejectUnknownAgentType() {
        AgentDefinitionService agentDefinitionService = Mockito.mock(AgentDefinitionService.class);
        Mockito.when(agentDefinitionService.findEnabledEntity("unknown")).thenReturn(Optional.empty());
        Mockito.when(agentDefinitionService.listEnabledEntities()).thenReturn(List.of());
        AssistantAgentRegistry registry = new AssistantAgentRegistry(
                List.of(new StubAssistantAgent("rd")),
                agentDefinitionService,
                Mockito.mock(DynamicAssistantAgent.class));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> registry.getRequired("unknown"));

        assertTrue(exception.getMessage().contains("Unknown agent"));
    }

    private record StubAssistantAgent(String agentType) implements AssistantAgent {

        @Override
        public String getAgentType() {
            return agentType;
        }

        @Override
        public Flux<ChatResponse> chatStream(String userId, String sessionId, String message) {
            return Flux.empty();
        }
    }
}
