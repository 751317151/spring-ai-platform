package com.huah.ai.platform.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.huah.ai.platform.agent.dto.AgentDefinitionUpsertRequest;
import com.huah.ai.platform.agent.entity.AgentDefinitionEntity;
import com.huah.ai.platform.agent.mapper.AgentDefinitionMapper;
import com.huah.ai.platform.agent.mapper.AgentRoleMapper;
import com.huah.ai.platform.common.util.SnowflakeIdGenerator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class AgentDefinitionServiceTest {

    @Mock
    private AgentDefinitionMapper agentDefinitionMapper;
    @Mock
    private AgentRoleMapper agentRoleMapper;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;
    @Mock
    private AssistantCapabilityResolverService assistantCapabilityResolverService;

    private AgentModelSupportService agentModelSupportService;
    private AgentDefinitionService service;

    @BeforeEach
    void setUp() {
        agentModelSupportService = new AgentModelSupportService();
        service = new AgentDefinitionService(
                agentDefinitionMapper,
                agentRoleMapper,
                new AssistantProfileCatalog(),
                agentModelSupportService,
                assistantCapabilityResolverService,
                jdbcTemplate,
                snowflakeIdGenerator);
        lenient().when(assistantCapabilityResolverService.normalizeSelectedToolCodes(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(assistantCapabilityResolverService.normalizeSelectedMcpServerCodes(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createRejectsReservedAgentCode() {
        AgentDefinitionUpsertRequest request = AgentDefinitionUpsertRequest.builder()
                .agentCode("multi")
                .agentName("Legal Assistant")
                .allowedRoles("ROLE_ADMIN")
                .assistantProfile("generic")
                .systemPrompt("You are a legal assistant")
                .build();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> service.create("admin", request));

        assertEquals("agentCode conflicts with reserved agent type: multi", exception.getMessage());
        verify(agentDefinitionMapper, never()).insert(any(AgentDefinitionEntity.class));
    }

    @Test
    void createPersistsNormalizedDefinition() {
        AgentModelSupportService.ModelDefinition modelDefinition = new AgentModelSupportService.ModelDefinition();
        modelDefinition.setId("gpt-4o-mini");
        agentModelSupportService.setRegistry(List.of(modelDefinition));
        AgentDefinitionUpsertRequest request = AgentDefinitionUpsertRequest.builder()
                .agentCode("Legal-Agent")
                .agentName("Legal Assistant")
                .allowedRoles("ROLE_ADMIN,ROLE_USER")
                .description("For legal team")
                .systemPrompt("Answer contract questions")
                .defaultModel("gpt-4o-mini")
                .enabled(true)
                .sortOrder(10)
                .dailyTokenLimit(200000)
                .build();
        when(agentDefinitionMapper.selectByAgentCode("legal-agent")).thenReturn(null);
        when(snowflakeIdGenerator.nextLongId()).thenReturn(9001L);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(
                        Map.of("id", 1L, "role_name", "ROLE_ADMIN"),
                        Map.of("id", 2L, "role_name", "ROLE_USER")));

        var response = service.create("admin", request);

        assertEquals("legal-agent", response.getAgentCode());
        assertEquals(9001L, response.getId());
        assertEquals("Legal Assistant", response.getAgentName());
        assertEquals("generic", response.getAssistantProfile());
        assertEquals("gpt-4o-mini", response.getDefaultModel());
        assertEquals(200000, response.getDailyTokenLimit());
        verify(agentDefinitionMapper).insert(any(AgentDefinitionEntity.class));
    }

    @Test
    void createRejectsUnsupportedAutoVariantDefaultModel() {
        AgentDefinitionUpsertRequest request = AgentDefinitionUpsertRequest.builder()
                .agentCode("legal-agent")
                .agentName("Legal Assistant")
                .allowedRoles("ROLE_ADMIN")
                .assistantProfile("generic")
                .systemPrompt("You are a legal assistant")
                .defaultModel("auto1")
                .build();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> service.create("admin", request));

        assertEquals("defaultModel is unsupported: auto1", exception.getMessage());
        verify(agentDefinitionMapper, never()).insert(any(AgentDefinitionEntity.class));
    }

    @Test
    void deleteRejectsSpecialDefinition() {
        when(agentDefinitionMapper.selectByAgentCode("mcp")).thenReturn(AgentDefinitionEntity.builder()
                .id(2011L)
                .agentCode("mcp")
                .systemDefined(true)
                .build());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> service.delete("mcp"));

        assertEquals("System-defined assistant cannot be deleted: mcp", exception.getMessage());
    }
}
