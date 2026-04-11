package com.huah.ai.platform.agent.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.huah.ai.platform.agent.config.AgentChatClientFactory;
import com.huah.ai.platform.agent.dto.SessionConfigResponse;
import com.huah.ai.platform.agent.entity.AgentDefinitionEntity;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;

class DynamicAssistantAgentTest {

    @Test
    void renderSystemPromptShouldInlineUserIdWithoutLeavingTemplatePlaceholders() {
        DynamicAssistantAgent agent = new DynamicAssistantAgent(
                mock(AgentDefinitionService.class),
                mock(AgentChatClientFactory.class),
                mock(ChatModel.class),
                mock(ChatMemory.class),
                mock(com.huah.ai.platform.agent.memory.ConversationMemoryService.class),
                new SessionRuntimeInstructionBuilder(),
                new AgentModelSupportService(),
                mock(AssistantCapabilityResolverService.class));

        AgentDefinitionEntity definition = AgentDefinitionEntity.builder()
                .agentCode("legal-agent")
                .agentName("Legal Assistant")
                .description("Handles policy and contract questions")
                .systemPrompt("Respond in JSON when needed. Current operator is {userId}. Example: {\"mode\":\"strict\"}")
                .build();

        String rendered = agent.renderSystemPrompt(definition, "alice");

        assertTrue(rendered.contains("Current operator is alice"));
        assertTrue(rendered.contains("{\"mode\":\"strict\"}"));
        assertTrue(rendered.contains("Current user: alice"));
        assertFalse(rendered.contains("Current operator is {userId}"));
    }

    @Test
    void buildChatOptionsShouldFallbackToAutoWhenDefinitionDefaultModelIsUnsupported() {
        DynamicAssistantAgent agent = new DynamicAssistantAgent(
                mock(AgentDefinitionService.class),
                mock(AgentChatClientFactory.class),
                mock(ChatModel.class),
                mock(ChatMemory.class),
                mock(com.huah.ai.platform.agent.memory.ConversationMemoryService.class),
                new SessionRuntimeInstructionBuilder(),
                new AgentModelSupportService(),
                mock(AssistantCapabilityResolverService.class));

        AgentDefinitionEntity definition = AgentDefinitionEntity.builder()
                .agentCode("legal-agent")
                .defaultModel("auto1")
                .build();

        ChatOptions options = agent.buildChatOptions(SessionConfigResponse.builder().build(), definition);

        assertNull(options.getModel());
    }
}
