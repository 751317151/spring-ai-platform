package com.huah.ai.platform.agent.service;

import com.huah.ai.platform.agent.entity.AgentDefinitionEntity;
import com.huah.ai.platform.agent.entity.AgentRoleEntity;
import com.huah.ai.platform.agent.mapper.AgentDefinitionMapper;
import com.huah.ai.platform.agent.mapper.AgentRoleMapper;
import com.huah.ai.platform.common.util.SnowflakeIdGenerator;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentDefinitionBootstrap implements ApplicationRunner {

    private final AgentDefinitionMapper agentDefinitionMapper;
    private final AgentRoleMapper agentRoleMapper;
    private final AssistantProfileCatalog assistantProfileCatalog;
    private final JdbcTemplate jdbcTemplate;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    @Override
    public void run(ApplicationArguments args) {
        for (AssistantProfileCatalog.BuiltInAssistantDefinition seed : assistantProfileCatalog.getBuiltInDefinitions()) {
            upsertBuiltInDefinition(seed);
        }
    }

    private void upsertBuiltInDefinition(AssistantProfileCatalog.BuiltInAssistantDefinition seed) {
        AgentDefinitionEntity existing = agentDefinitionMapper.selectByAgentCode(seed.agentCode());
        if (existing == null) {
            LocalDateTime now = LocalDateTime.now();
            agentDefinitionMapper.insert(AgentDefinitionEntity.builder()
                    .id(snowflakeIdGenerator.nextLongId())
                    .agentCode(seed.agentCode())
                    .agentName(seed.agentName())
                    .description(seed.description())
                    .icon(seed.icon())
                    .color(seed.color())
                    .systemPrompt(seed.systemPrompt())
                    .defaultModel(seed.defaultModel())
                    .toolCodes(seed.toolCodes())
                    .mcpServerCodes(seed.mcpServerCodes())
                    .enabled(seed.enabled())
                    .sortOrder(seed.sortOrder())
                    .dailyTokenLimit(seed.dailyTokenLimit())
                    .assistantProfile(seed.assistantProfile())
                    .systemDefined(seed.systemDefined())
                    .createdBy(seed.createdBy())
                    .updatedBy(seed.updatedBy())
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
            syncRoles(seed.agentCode(), seed.allowedRoles());
            return;
        }

        agentDefinitionMapper.updateById(AgentDefinitionEntity.builder()
                .id(existing.getId())
                .agentCode(existing.getAgentCode())
                .agentName(preferExisting(existing.getAgentName(), seed.agentName()))
                .description(preferExisting(existing.getDescription(), seed.description()))
                .icon(preferExisting(existing.getIcon(), seed.icon()))
                .color(preferExisting(existing.getColor(), seed.color()))
                .systemPrompt(preferExisting(existing.getSystemPrompt(), seed.systemPrompt()))
                .defaultModel(preferExisting(existing.getDefaultModel(), seed.defaultModel()))
                .toolCodes(preferExisting(existing.getToolCodes(), seed.toolCodes()))
                .mcpServerCodes(preferExisting(existing.getMcpServerCodes(), seed.mcpServerCodes()))
                .enabled(existing.getEnabled() == null ? seed.enabled() : existing.getEnabled())
                .sortOrder(existing.getSortOrder() == null ? seed.sortOrder() : existing.getSortOrder())
                .dailyTokenLimit(existing.getDailyTokenLimit() == null ? seed.dailyTokenLimit() : existing.getDailyTokenLimit())
                .assistantProfile(resolveAssistantProfile(existing.getAssistantProfile(), seed.assistantProfile()))
                .systemDefined(Boolean.TRUE)
                .createdBy(preferExisting(existing.getCreatedBy(), seed.createdBy()))
                .updatedBy(preferExisting(existing.getUpdatedBy(), seed.updatedBy()))
                .createdAt(existing.getCreatedAt() == null ? LocalDateTime.now() : existing.getCreatedAt())
                .updatedAt(LocalDateTime.now())
                .build());
        syncRoles(seed.agentCode(), seed.allowedRoles());
    }

    private void syncRoles(String agentCode, List<String> roleNames) {
        if (roleNames == null || roleNames.isEmpty()) {
            return;
        }
        if (!agentRoleMapper.selectRoleNamesByAgentCode(agentCode).isEmpty()) {
            return;
        }
        for (String roleName : roleNames) {
            List<Long> roleIds = jdbcTemplate.query(
                    "SELECT id FROM ai_roles WHERE role_name = ?",
                    (rs, rowNum) -> rs.getLong("id"),
                    roleName);
            if (roleIds.isEmpty()) {
                continue;
            }
            agentRoleMapper.insert(AgentRoleEntity.builder()
                    .agentCode(agentCode)
                    .roleId(roleIds.get(0))
                    .build());
        }
    }

    private String resolveAssistantProfile(String existingProfile, String fallbackProfile) {
        if (assistantProfileCatalog.supportsProfile(existingProfile)) {
            return assistantProfileCatalog.normalizeProfile(existingProfile);
        }
        return fallbackProfile;
    }

    private String preferExisting(String existing, String fallback) {
        if (existing == null || existing.isBlank()) {
            return fallback;
        }
        return existing;
    }
}
