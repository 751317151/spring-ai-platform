package com.huah.ai.platform.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.huah.ai.platform.agent.dto.AgentDefinitionResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentMetadataServiceTest {

    @Mock
    private AssistantAgentRegistry assistantAgentRegistry;

    @Mock
    private AgentDefinitionService agentDefinitionService;

    private AgentMetadataService service;

    @BeforeEach
    void setUp() {
        service = new AgentMetadataService(assistantAgentRegistry, agentDefinitionService, new AssistantProfileCatalog());
    }

    @Test
    void listUsesUnifiedDefinitionsAndProfileCapabilities() {
        when(assistantAgentRegistry.supports("legal-agent")).thenReturn(true);
        when(agentDefinitionService.listEnabled()).thenReturn(List.of(
                AgentDefinitionResponse.builder()
                        .agentCode("legal-agent")
                        .agentName("法务助手")
                        .assistantProfile("generic")
                        .description("法务专属口径")
                        .icon("LG")
                        .color("#2563eb")
                        .defaultModel("gpt-4o-mini")
                        .enabled(true)
                        .build()
        ));

        var response = service.list();
        var dynamic = response.getAgents().stream()
                .filter(item -> "legal-agent".equals(item.getAgentType()))
                .findFirst()
                .orElseThrow();

        assertEquals("法务助手", dynamic.getName());
        assertEquals("gpt-4o-mini", dynamic.getDefaultModel());
        assertTrue(dynamic.isRegistered());
        assertEquals(false, dynamic.isSupportsKnowledge());
        assertEquals(false, dynamic.isSupportsTools());

        var multi = response.getAgents().stream()
                .filter(item -> "multi".equals(item.getAgentType()))
                .findFirst()
                .orElseThrow();

        assertTrue(multi.isSupportsMultiAgentMode());
    }
}
