package com.huah.ai.platform.agent.service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistantAgentRegistryTest {

    @Test
    void shouldResolveRegisteredAgentByType() {
        StubAssistantAgent rdAgent = new StubAssistantAgent("rd");
        StubAssistantAgent codeAgent = new StubAssistantAgent("code");
        AssistantAgentRegistry registry = new AssistantAgentRegistry(List.of(rdAgent, codeAgent));

        assertTrue(registry.supports("rd"));
        assertTrue(registry.supports("code"));
        assertFalse(registry.supports("mcp"));
        assertEquals(rdAgent, registry.getRequired("rd"));
        assertEquals(codeAgent, registry.getRequired("code"));
    }

    @Test
    void shouldRejectDuplicateAgentType() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> new AssistantAgentRegistry(List.of(
                        new StubAssistantAgent("rd"),
                        new StubAssistantAgent("rd")
                )));

        assertTrue(exception.getMessage().contains("Duplicate assistant agent type"));
    }

    @Test
    void shouldRejectUnknownAgentType() {
        AssistantAgentRegistry registry = new AssistantAgentRegistry(List.of(new StubAssistantAgent("rd")));

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
        public AgentChatResult chat(String userId, String sessionId, String message) {
            return new AgentChatResult(message, 0, 0);
        }

        @Override
        public Flux<ChatResponse> chatStream(String userId, String sessionId, String message) {
            return Flux.empty();
        }
    }
}
