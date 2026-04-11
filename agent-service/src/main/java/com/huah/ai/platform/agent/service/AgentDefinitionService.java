package com.huah.ai.platform.agent.service;

import com.huah.ai.platform.agent.dto.AgentDefinitionResponse;
import com.huah.ai.platform.agent.dto.AgentDefinitionUpsertRequest;
import com.huah.ai.platform.agent.entity.AgentDefinitionEntity;
import com.huah.ai.platform.agent.entity.AgentRoleEntity;
import com.huah.ai.platform.agent.mapper.AgentDefinitionMapper;
import com.huah.ai.platform.agent.mapper.AgentRoleMapper;
import com.huah.ai.platform.common.util.SnowflakeIdGenerator;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentDefinitionService {

    private static final Pattern AGENT_CODE_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{1,63}$");
    private static final String DEFAULT_ICON = "AI";
    private static final String DEFAULT_COLOR = "#6b7280";
    private static final String DEFAULT_MODEL = "auto";
    private static final int DEFAULT_DAILY_TOKEN_LIMIT = 100000;
    private static final String RESERVED_AGENT_CODE = "multi";

    private final AgentDefinitionMapper agentDefinitionMapper;
    private final AgentRoleMapper agentRoleMapper;
    private final AssistantProfileCatalog assistantProfileCatalog;
    private final AgentModelSupportService agentModelSupportService;
    private final AssistantCapabilityResolverService assistantCapabilityResolverService;
    private final JdbcTemplate jdbcTemplate;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    public AgentDefinitionService(
            AgentDefinitionMapper agentDefinitionMapper,
            AgentRoleMapper agentRoleMapper,
            AssistantProfileCatalog assistantProfileCatalog,
            AgentModelSupportService agentModelSupportService,
            AssistantCapabilityResolverService assistantCapabilityResolverService,
            JdbcTemplate jdbcTemplate,
            SnowflakeIdGenerator snowflakeIdGenerator) {
        this.agentDefinitionMapper = agentDefinitionMapper;
        this.agentRoleMapper = agentRoleMapper;
        this.assistantProfileCatalog = assistantProfileCatalog;
        this.agentModelSupportService = agentModelSupportService;
        this.assistantCapabilityResolverService = assistantCapabilityResolverService;
        this.jdbcTemplate = jdbcTemplate;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
    }

    public List<AgentDefinitionResponse> listAll() {
        return toResponses(agentDefinitionMapper.selectAllDefinitions());
    }

    public List<AgentDefinitionResponse> listEnabled() {
        return toResponses(agentDefinitionMapper.selectEnabledDefinitions());
    }

    public List<AgentDefinitionEntity> listEnabledEntities() {
        List<AgentDefinitionEntity> entities = agentDefinitionMapper.selectEnabledDefinitions();
        return entities == null ? List.of() : entities;
    }

    public AgentDefinitionResponse getRequired(String agentCode) {
        return toResponse(getRequiredEntity(agentCode));
    }

    public AgentDefinitionEntity getRequiredEntity(String agentCode) {
        AgentDefinitionEntity entity = agentDefinitionMapper.selectByAgentCode(agentCode);
        if (entity == null) {
            throw new IllegalArgumentException("Agent definition not found: " + agentCode);
        }
        return entity;
    }

    public AgentDefinitionEntity getRequiredEnabledEntity(String agentCode) {
        AgentDefinitionEntity entity = getRequiredEntity(agentCode);
        if (!Boolean.TRUE.equals(entity.getEnabled())) {
            throw new IllegalArgumentException("Agent definition is disabled: " + agentCode);
        }
        return entity;
    }

    public Optional<AgentDefinitionEntity> findEnabledEntity(String agentCode) {
        AgentDefinitionEntity entity = agentDefinitionMapper.selectByAgentCode(agentCode);
        if (entity == null || !Boolean.TRUE.equals(entity.getEnabled())) {
            return Optional.empty();
        }
        return Optional.of(entity);
    }

    @Transactional
    public AgentDefinitionResponse create(String operator, AgentDefinitionUpsertRequest request) {
        AgentDefinitionEntity entity = buildEntityForCreate(operator, request);
        if (agentDefinitionMapper.selectByAgentCode(entity.getAgentCode()) != null) {
            throw new IllegalArgumentException("Agent definition already exists: " + entity.getAgentCode());
        }
        agentDefinitionMapper.insert(entity);
        replaceAgentRoles(entity.getAgentCode(), request.getAllowedRoles());
        return toResponse(entity);
    }

    @Transactional
    public AgentDefinitionResponse update(String agentCode, String operator, AgentDefinitionUpsertRequest request) {
        AgentDefinitionEntity existing = getRequiredEntity(agentCode);
        if (request.getAgentCode() != null && !agentCode.equals(normalizeAgentCode(request.getAgentCode()))) {
            throw new IllegalArgumentException("Agent code cannot be changed");
        }
        AgentDefinitionEntity entity = buildEntityForUpdate(existing, operator, request);
        agentDefinitionMapper.updateById(entity);
        replaceAgentRoles(entity.getAgentCode(), request.getAllowedRoles());
        return toResponse(entity);
    }

    @Transactional
    public void delete(String agentCode) {
        AgentDefinitionEntity existing = getRequiredEntity(agentCode);
        if (Boolean.TRUE.equals(existing.getSystemDefined())) {
            throw new IllegalArgumentException("System-defined assistant cannot be deleted: " + agentCode);
        }
        agentRoleMapper.deleteByAgentCode(agentCode);
        int affected = agentDefinitionMapper.deleteByAgentCode(agentCode);
        if (affected != 1) {
            throw new IllegalStateException("Unexpected delete result for agent definition: " + agentCode);
        }
    }

    private AgentDefinitionEntity buildEntityForCreate(String operator, AgentDefinitionUpsertRequest request) {
        String agentCode = normalizeAgentCode(request.getAgentCode());
        validateAgentCode(agentCode);
        LocalDateTime now = LocalDateTime.now();
        return AgentDefinitionEntity.builder()
                .id(snowflakeIdGenerator.nextLongId())
                .agentCode(agentCode)
                .agentName(requireText(request.getAgentName(), "agentName"))
                .description(trimToNull(request.getDescription()))
                .icon(defaultIfBlank(request.getIcon(), DEFAULT_ICON))
                .color(defaultIfBlank(request.getColor(), DEFAULT_COLOR))
                .systemPrompt(requireText(request.getSystemPrompt(), "systemPrompt"))
                .defaultModel(normalizeDefaultModel(request.getDefaultModel()))
                .toolCodes(normalizeToolCodes(request.getToolCodes()))
                .mcpServerCodes(normalizeMcpServerCodes(request.getMcpServerCodes()))
                .enabled(request.getEnabled() == null || request.getEnabled())
                .sortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder())
                .dailyTokenLimit(request.getDailyTokenLimit() == null ? DEFAULT_DAILY_TOKEN_LIMIT : request.getDailyTokenLimit())
                .assistantProfile(normalizeAssistantProfile(request.getAssistantProfile()))
                .systemDefined(false)
                .createdBy(operator)
                .updatedBy(operator)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private AgentDefinitionEntity buildEntityForUpdate(
            AgentDefinitionEntity existing, String operator, AgentDefinitionUpsertRequest request) {
        String assistantProfile = resolveAssistantProfileForUpdate(existing, request);
        if (Boolean.TRUE.equals(existing.getSystemDefined()) && !existing.getAssistantProfile().equals(assistantProfile)) {
            throw new IllegalArgumentException("System-defined assistant profile cannot be changed");
        }
        return AgentDefinitionEntity.builder()
                .id(existing.getId())
                .agentCode(existing.getAgentCode())
                .agentName(requireText(request.getAgentName(), "agentName"))
                .description(trimToNull(request.getDescription()))
                .icon(defaultIfBlank(request.getIcon(), DEFAULT_ICON))
                .color(defaultIfBlank(request.getColor(), DEFAULT_COLOR))
                .systemPrompt(requireText(request.getSystemPrompt(), "systemPrompt"))
                .defaultModel(normalizeDefaultModel(request.getDefaultModel()))
                .toolCodes(resolveToolCodesForUpdate(existing, request))
                .mcpServerCodes(resolveMcpServerCodesForUpdate(existing, request))
                .enabled(request.getEnabled() == null ? existing.getEnabled() : request.getEnabled())
                .sortOrder(request.getSortOrder() == null ? existing.getSortOrder() : request.getSortOrder())
                .dailyTokenLimit(request.getDailyTokenLimit() == null ? existing.getDailyTokenLimit() : request.getDailyTokenLimit())
                .assistantProfile(assistantProfile)
                .systemDefined(existing.getSystemDefined())
                .createdBy(existing.getCreatedBy())
                .updatedBy(operator)
                .createdAt(existing.getCreatedAt())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private void validateAgentCode(String agentCode) {
        if (!AGENT_CODE_PATTERN.matcher(agentCode).matches()) {
            throw new IllegalArgumentException("agentCode must match ^[a-z0-9][a-z0-9-]{1,63}$");
        }
        if (RESERVED_AGENT_CODE.equals(agentCode)) {
            throw new IllegalArgumentException("agentCode conflicts with reserved agent type: " + agentCode);
        }
    }

    private String normalizeAgentCode(String agentCode) {
        return requireText(agentCode, "agentCode").toLowerCase(Locale.ROOT);
    }

    private String normalizeAssistantProfile(String assistantProfile) {
        String resolved = trimToNull(assistantProfile);
        if (resolved == null) {
            return AssistantProfileCatalog.GENERIC_PROFILE;
        }
        return assistantProfileCatalog.getRequiredProfile(resolved).profileCode();
    }

    private String resolveAssistantProfileForUpdate(
            AgentDefinitionEntity existing, AgentDefinitionUpsertRequest request) {
        if (request.getAssistantProfile() == null) {
            return existing.getAssistantProfile();
        }
        return normalizeAssistantProfile(request.getAssistantProfile());
    }

    private String requireText(String value, String fieldName) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String defaultIfBlank(String value, String defaultValue) {
        String normalized = trimToNull(value);
        return normalized == null ? defaultValue : normalized;
    }

    private String normalizeDefaultModel(String defaultModel) {
        return agentModelSupportService.normalizeDefaultModel(defaultModel);
    }

    private String normalizeToolCodes(String toolCodes) {
        return assistantCapabilityResolverService.normalizeSelectedToolCodes(toolCodes);
    }

    private String normalizeMcpServerCodes(String mcpServerCodes) {
        return assistantCapabilityResolverService.normalizeSelectedMcpServerCodes(mcpServerCodes);
    }

    private String resolveToolCodesForUpdate(AgentDefinitionEntity existing, AgentDefinitionUpsertRequest request) {
        if (request.getToolCodes() == null) {
            return existing.getToolCodes();
        }
        return normalizeToolCodes(request.getToolCodes());
    }

    private String resolveMcpServerCodesForUpdate(AgentDefinitionEntity existing, AgentDefinitionUpsertRequest request) {
        if (request.getMcpServerCodes() == null) {
            return existing.getMcpServerCodes();
        }
        return normalizeMcpServerCodes(request.getMcpServerCodes());
    }

    private void replaceAgentRoles(String agentCode, String allowedRolesCsv) {
        List<String> roleNames = normalizeRoleNames(allowedRolesCsv);
        if (roleNames.isEmpty()) {
            throw new IllegalArgumentException("allowedRoles is required");
        }
        List<Long> roleIds = resolveRoleIds(roleNames);
        agentRoleMapper.deleteByAgentCode(agentCode);
        for (Long roleId : roleIds) {
            agentRoleMapper.insert(AgentRoleEntity.builder()
                    .agentCode(agentCode)
                    .roleId(roleId)
                    .build());
        }
    }

    private List<String> normalizeRoleNames(String allowedRolesCsv) {
        Set<String> roleNames = new LinkedHashSet<>();
        if (allowedRolesCsv != null) {
            for (String role : allowedRolesCsv.split(",")) {
                String trimmed = role.trim();
                if (!trimmed.isEmpty()) {
                    roleNames.add(trimmed.toUpperCase(Locale.ROOT));
                }
            }
        }
        return List.copyOf(roleNames);
    }

    private List<Long> resolveRoleIds(List<String> roleNames) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, role_name FROM ai_roles WHERE role_name IN (" +
                        roleNames.stream().map(item -> "?").collect(Collectors.joining(",")) + ")",
                roleNames.toArray());
        if (rows.size() != roleNames.size()) {
            Set<String> existingNames = rows.stream()
                    .map(row -> String.valueOf(row.get("role_name")))
                    .collect(Collectors.toSet());
            List<String> missing = roleNames.stream().filter(roleName -> !existingNames.contains(roleName)).toList();
            throw new IllegalArgumentException("roles not found: " + String.join(",", missing));
        }
        return rows.stream()
                .map(row -> (Number) row.get("id"))
                .map(Number::longValue)
                .toList();
    }

    private List<AgentDefinitionResponse> toResponses(List<AgentDefinitionEntity> entities) {
        List<AgentDefinitionResponse> responses = new ArrayList<>();
        if (entities == null) {
            return responses;
        }
        for (AgentDefinitionEntity entity : entities) {
            responses.add(toResponse(entity));
        }
        return responses;
    }

    private AgentDefinitionResponse toResponse(AgentDefinitionEntity entity) {
        List<String> roleNames = agentRoleMapper.selectRoleNamesByAgentCode(entity.getAgentCode());
        return AgentDefinitionResponse.builder()
                .id(entity.getId())
                .agentCode(entity.getAgentCode())
                .agentName(entity.getAgentName())
                .description(entity.getDescription())
                .icon(entity.getIcon())
                .color(entity.getColor())
                .systemPrompt(entity.getSystemPrompt())
                .defaultModel(entity.getDefaultModel())
                .toolCodes(entity.getToolCodes())
                .mcpServerCodes(entity.getMcpServerCodes())
                .enabled(entity.getEnabled())
                .sortOrder(entity.getSortOrder())
                .dailyTokenLimit(entity.getDailyTokenLimit())
                .allowedRoles(roleNames == null ? "" : String.join(",", roleNames))
                .assistantProfile(entity.getAssistantProfile())
                .systemDefined(entity.getSystemDefined())
                .createdBy(entity.getCreatedBy())
                .updatedBy(entity.getUpdatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
