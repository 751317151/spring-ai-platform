package com.huah.ai.platform.agent.security;

import com.huah.ai.platform.agent.audit.ToolExecutionContext;
import com.huah.ai.platform.agent.config.ToolsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ToolSecurityService {

    private static final String WILDCARD = "*";
    private static final String DEFAULT_DATA_SOURCE = "default";

    private final ToolsProperties toolsProperties;

    public record DataScopeTarget(String schema, String table) {
    }

    public void validateToolAccess(String toolName, ToolExecutionContext.Context context) {
        ToolAccessDecision decision = decideToolAccess(context != null ? context.getAgentType() : null, toolName);
        if (!decision.isAllowed()) {
            throw new ToolAccessDeniedException(
                    decision.getReasonCode(),
                    decision.getReasonMessage(),
                    decision.getResource(),
                    decision.getDetail()
            );
        }
    }

    public void validateConnectorAccess(String connectorCode, ToolExecutionContext.Context context) {
        ToolAccessDecision decision = decideConnectorAccess(context != null ? context.getAgentType() : null, connectorCode);
        if (!decision.isAllowed()) {
            throw new ToolAccessDeniedException(
                    decision.getReasonCode(),
                    decision.getReasonMessage(),
                    decision.getResource(),
                    decision.getDetail()
            );
        }
    }

    public void validateConnectorPathAccess(String connectorCode,
                                            String path,
                                            ToolExecutionContext.Context context,
                                            Collection<String> connectorPathPrefixes) {
        ToolAccessDecision decision = decideConnectorPathAccess(
                context != null ? context.getAgentType() : null,
                connectorCode,
                path,
                connectorPathPrefixes
        );
        if (!decision.isAllowed()) {
            throw new ToolAccessDeniedException(
                    decision.getReasonCode(),
                    decision.getReasonMessage(),
                    decision.getResource(),
                    decision.getDetail()
            );
        }
    }

    public ToolAccessDecision decideToolAccess(String agentType, String toolName) {
        if (!isSecurityEnabled()) {
            return allow("SECURITY_DISABLED", "Tool security is disabled", "tool:" + toolName);
        }
        if (isAllowed(agentType, toolName, toolsProperties.getSecurity().getAgentToolAllowlist())) {
            return allow("TOOL_ALLOWED", "The current agent can call this tool", "tool:" + toolName);
        }
        return deny("TOOL_DENIED", "The current agent is not allowed to call this tool", "tool:" + toolName);
    }

    public ToolAccessDecision decideConnectorAccess(String agentType, String connectorCode) {
        if (!isSecurityEnabled()) {
            return allow("SECURITY_DISABLED", "Connector security is disabled", "connector:" + connectorCode);
        }
        if (isAllowed(agentType, connectorCode, toolsProperties.getSecurity().getAgentConnectorAllowlist())) {
            return allow("CONNECTOR_ALLOWED", "The current agent can access this connector", "connector:" + connectorCode);
        }
        return deny("CONNECTOR_DENIED", "The current agent is not allowed to access this connector", "connector:" + connectorCode);
    }

    public ToolAccessDecision decideConnectorPathAccess(String agentType,
                                                        String connectorCode,
                                                        String path,
                                                        Collection<String> connectorPathPrefixes) {
        ToolAccessDecision connectorDecision = decideConnectorAccess(agentType, connectorCode);
        if (!connectorDecision.isAllowed()) {
            return connectorDecision;
        }
        List<String> resourcePrefixes = getAllowedConnectorResourcePrefixes(agentType, connectorCode, connectorPathPrefixes);
        if (resourcePrefixes.isEmpty() || resourcePrefixes.contains(WILDCARD)) {
            return allow("CONNECTOR_RESOURCE_ALLOWED", "The current agent can access this connector resource", "connector:" + connectorCode + path);
        }
        boolean matched = resourcePrefixes.stream().anyMatch(prefix -> matchesPathPrefix(path, prefix));
        if (matched) {
            return allow("CONNECTOR_RESOURCE_ALLOWED", "The current agent can access this connector resource", "connector:" + connectorCode + path);
        }
        return deny(
                "CONNECTOR_RESOURCE_DENIED",
                "The requested connector path is outside the allowed resource boundary",
                "connector:" + connectorCode + path
        );
    }

    public ToolAccessDecision decideMcpServerAccess(String agentType, String serverCode) {
        if (!isSecurityEnabled()) {
            return allow("SECURITY_DISABLED", "MCP security is disabled", "mcp:" + serverCode);
        }
        if (isAllowed(agentType, serverCode, toolsProperties.getSecurity().getAgentMcpServerAllowlist())) {
            return allow("MCP_ALLOWED", "The current agent can access this MCP server", "mcp:" + serverCode);
        }
        return deny("MCP_DENIED", "The current agent is not allowed to access this MCP server", "mcp:" + serverCode);
    }

    public ToolAccessDecision decideDataScopeAccess(String agentType, String schema, Collection<String> tables) {
        List<DataScopeTarget> targets = new ArrayList<>();
        Collection<String> effectiveTables = tables == null ? List.of() : tables;
        if (effectiveTables.isEmpty()) {
            targets.add(new DataScopeTarget(schema, null));
        } else {
            for (String table : effectiveTables) {
                targets.add(new DataScopeTarget(schema, table));
            }
        }
        return decideDataScopeAccess(agentType, targets);
    }

    public ToolAccessDecision decideDataScopeAccess(String agentType, Collection<DataScopeTarget> targets) {
        List<String> allowlist = getAllowedDataScopes(agentType);
        List<String> allowedDataSources = getAllowedDataSources(agentType);
        List<String> crossSchemaAllowlist = getAllowedCrossSchemaAccess(agentType);
        List<DataScopeTarget> normalizedTargets = normalizeTargets(targets);
        String resource = buildDataScopeResource(normalizedTargets);

        if (!isSecurityEnabled()) {
            return allow("DATA_SCOPE_ALLOWED", "Data scope security is not restricted for this request", resource);
        }

        boolean dataScopeRestricted = !allowlist.isEmpty() && !allowlist.contains(WILDCARD);
        boolean dataSourceRestricted = !allowedDataSources.isEmpty() && !allowedDataSources.contains(WILDCARD);
        boolean crossSchemaRestricted = !crossSchemaAllowlist.isEmpty() && !crossSchemaAllowlist.contains(WILDCARD);
        if (!dataScopeRestricted && !dataSourceRestricted && !crossSchemaRestricted) {
            return allow("DATA_SCOPE_ALLOWED", "Data scope security is not restricted for this request", resource);
        }

        Set<String> schemas = new LinkedHashSet<>();
        Map<String, String> schemaToDataSource = new java.util.LinkedHashMap<>();
        for (DataScopeTarget target : normalizedTargets) {
            String normalizedSchema = normalizeSchema(target.schema());
            schemas.add(normalizedSchema);
            schemaToDataSource.put(normalizedSchema, resolveDataSource(normalizedSchema));
        }

        if (schemas.size() > 1) {
            ToolAccessDecision crossSchemaDecision = validateCrossSchemaAccess(
                    schemas,
                    schemaToDataSource,
                    crossSchemaAllowlist,
                    resource
            );
            if (!crossSchemaDecision.isAllowed()) {
                return crossSchemaDecision;
            }
        }

        if (dataSourceRestricted) {
            for (Map.Entry<String, String> entry : schemaToDataSource.entrySet()) {
                if (!matchesAllowedDataSource(allowedDataSources, entry.getValue())) {
                    return deny(
                            "DATA_SOURCE_DENIED",
                            "The requested schema is bound to a data source outside the current agent boundary",
                            resource,
                            "schema=" + entry.getKey() + ", dataSource=" + entry.getValue() + ", allowed=" + allowedDataSources
                    );
                }
            }
        }

        if (dataScopeRestricted) {
            for (DataScopeTarget target : normalizedTargets) {
                String normalizedSchema = normalizeSchema(target.schema());
                if (target.table() == null || target.table().isBlank()) {
                    if (!matchesAnySchemaScope(allowlist, normalizedSchema)) {
                        return deny(
                                "DATA_SCHEMA_DENIED",
                                "The requested schema is outside the current agent data scope",
                                "data:" + normalizedSchema + ".*",
                                "schema=" + normalizedSchema + ", allowedScopes=" + allowlist
                        );
                    }
                    continue;
                }
                if (!matchesAnyDataScope(allowlist, normalizedSchema, target.table())) {
                    return deny(
                            "DATA_SCOPE_DENIED",
                            "The requested table is outside the current agent data scope",
                            "data:" + normalizedSchema + "." + target.table(),
                            "schema=" + normalizedSchema + ", table=" + target.table() + ", allowedScopes=" + allowlist
                    );
                }
            }
        }

        return allow(
                "DATA_SCOPE_ALLOWED",
                "Data scope validation passed",
                resource,
                "schemas=" + schemas + ", dataSources=" + new LinkedHashSet<>(schemaToDataSource.values())
        );
    }

    public List<String> getAllowedTools(String agentType) {
        return getAllowedValues(agentType, toolsProperties.getSecurity().getAgentToolAllowlist());
    }

    public List<String> getAllowedConnectors(String agentType) {
        return getAllowedValues(agentType, toolsProperties.getSecurity().getAgentConnectorAllowlist());
    }

    public List<String> getAllowedMcpServers(String agentType) {
        return getAllowedValues(agentType, toolsProperties.getSecurity().getAgentMcpServerAllowlist());
    }

    public List<String> getAllowedDataScopes(String agentType) {
        return getAllowedValues(agentType, toolsProperties.getSecurity().getAgentDataScopeAllowlist());
    }

    public List<String> getAllowedDataSources(String agentType) {
        return getAllowedValues(agentType, toolsProperties.getSecurity().getAgentDataSourceAllowlist());
    }

    public List<String> getAllowedCrossSchemaAccess(String agentType) {
        return getAllowedValues(agentType, toolsProperties.getSecurity().getAgentCrossSchemaAccessAllowlist());
    }

    public String resolveDataSource(String schema) {
        if (toolsProperties == null || toolsProperties.getSecurity() == null) {
            return DEFAULT_DATA_SOURCE;
        }
        Map<String, String> bindings = toolsProperties.getSecurity().getSchemaDataSourceBindings();
        if (bindings == null || bindings.isEmpty()) {
            return DEFAULT_DATA_SOURCE;
        }
        String matched = bindings.get(normalizeSchema(schema));
        return matched == null || matched.isBlank() ? DEFAULT_DATA_SOURCE : matched.trim();
    }

    public List<String> getAllowedConnectorResourcePrefixes(String agentType,
                                                            String connectorCode,
                                                            Collection<String> fallbackPrefixes) {
        List<String> configured = getNestedAllowedValues(
                agentType,
                connectorCode,
                toolsProperties.getSecurity().getAgentConnectorResourceAllowlist()
        );
        if (!configured.isEmpty()) {
            return configured;
        }
        return normalize(fallbackPrefixes == null ? List.of() : new ArrayList<>(fallbackPrefixes));
    }

    public List<String> getAllowedMcpTools(String agentType, String serverCode) {
        return getNestedAllowedValues(agentType, serverCode, toolsProperties.getSecurity().getAgentMcpToolAllowlist());
    }

    public List<String> filterAuthorizedConnectors(String agentType, Collection<String> connectorCodes) {
        if (connectorCodes == null || connectorCodes.isEmpty()) {
            return List.of();
        }
        if (!isSecurityEnabled()) {
            return new ArrayList<>(connectorCodes);
        }
        List<String> allowlist = getAllowedConnectors(agentType);
        if (allowlist.isEmpty() || allowlist.contains(WILDCARD)) {
            return new ArrayList<>(connectorCodes);
        }
        return connectorCodes.stream().filter(allowlist::contains).toList();
    }

    public List<String> filterAuthorizedMcpServers(String agentType, Collection<String> serverCodes) {
        if (serverCodes == null || serverCodes.isEmpty()) {
            return List.of();
        }
        if (!isSecurityEnabled()) {
            return new ArrayList<>(serverCodes);
        }
        List<String> allowlist = getAllowedMcpServers(agentType);
        if (allowlist.isEmpty() || allowlist.contains(WILDCARD)) {
            return new ArrayList<>(serverCodes);
        }
        return serverCodes.stream().filter(allowlist::contains).toList();
    }

    public boolean isSecurityEnabled() {
        return toolsProperties != null
                && toolsProperties.getSecurity() != null
                && toolsProperties.getSecurity().isEnabled();
    }

    private boolean isAllowed(String agentType, String value, Map<String, List<String>> rules) {
        List<String> allowlist = getAllowedValues(agentType, rules);
        if (allowlist.isEmpty()) {
            return true;
        }
        return allowlist.contains(WILDCARD) || allowlist.contains(value);
    }

    private List<String> getAllowedValues(String agentType, Map<String, List<String>> rules) {
        if (rules == null || rules.isEmpty()) {
            return List.of();
        }
        List<String> direct = normalize(rules.get(agentType));
        if (!direct.isEmpty()) {
            return direct;
        }
        return normalize(rules.get(WILDCARD));
    }

    private List<String> getNestedAllowedValues(String agentType,
                                                String resourceCode,
                                                Map<String, Map<String, List<String>>> rules) {
        if (rules == null || rules.isEmpty()) {
            return List.of();
        }
        Map<String, List<String>> byAgent = rules.get(agentType);
        if (byAgent != null) {
            List<String> direct = normalize(byAgent.get(resourceCode));
            if (!direct.isEmpty()) {
                return direct;
            }
            List<String> agentWildcard = normalize(byAgent.get(WILDCARD));
            if (!agentWildcard.isEmpty()) {
                return agentWildcard;
            }
        }
        Map<String, List<String>> wildcardAgent = rules.get(WILDCARD);
        if (wildcardAgent != null) {
            List<String> direct = normalize(wildcardAgent.get(resourceCode));
            if (!direct.isEmpty()) {
                return direct;
            }
            return normalize(wildcardAgent.get(WILDCARD));
        }
        return List.of();
    }

    private boolean matchesAnyDataScope(List<String> rules, String schema, String table) {
        for (String rule : rules) {
            if (rule == null || rule.isBlank()) {
                continue;
            }
            String normalized = rule.trim();
            if (WILDCARD.equals(normalized)) {
                return true;
            }
            String[] parts = normalized.split("\\.", 2);
            if (parts.length == 1) {
                if (matchesToken(table, parts[0])) {
                    return true;
                }
                continue;
            }
            if (matchesToken(schema, parts[0]) && matchesToken(table, parts[1])) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesAnySchemaScope(List<String> rules, String schema) {
        for (String rule : rules) {
            if (rule == null || rule.isBlank()) {
                continue;
            }
            String normalized = rule.trim();
            if (WILDCARD.equals(normalized)) {
                return true;
            }
            String[] parts = normalized.split("\\.", 2);
            if (parts.length == 2 && matchesToken(schema, parts[0])) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesToken(String value, String rule) {
        if (WILDCARD.equals(rule)) {
            return true;
        }
        return value != null && value.equalsIgnoreCase(rule);
    }

    private boolean matchesPathPrefix(String path, String prefix) {
        if (prefix == null || prefix.isBlank() || WILDCARD.equals(prefix.trim())) {
            return true;
        }
        return path != null && path.startsWith(prefix.trim());
    }

    private List<String> normalize(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                normalized.add(value.trim());
            }
        }
        return new ArrayList<>(normalized);
    }

    private ToolAccessDecision validateCrossSchemaAccess(Set<String> schemas,
                                                         Map<String, String> schemaToDataSource,
                                                         List<String> crossSchemaAllowlist,
                                                         String resource) {
        List<String> pairs = buildSchemaPairs(schemas);
        if (crossSchemaAllowlist.isEmpty()) {
            return deny(
                    "DATA_CROSS_SCHEMA_DENIED",
                    "Cross-schema queries are not allowed for the current agent",
                    resource,
                    "requestedSchemas=" + schemas + ", requestedPairs=" + pairs
            );
        }
        for (String pair : pairs) {
            if (!matchesCrossSchemaRule(pair, crossSchemaAllowlist)) {
                return deny(
                        "DATA_CROSS_SCHEMA_DENIED",
                        "The requested schema combination is outside the current agent boundary",
                        resource,
                        "requestedPair=" + pair + ", allowedPairs=" + crossSchemaAllowlist
                );
            }
        }
        Set<String> dataSources = new LinkedHashSet<>(schemaToDataSource.values());
        if (dataSources.size() > 1) {
            return deny(
                    "DATA_CROSS_SOURCE_DENIED",
                    "The requested schema combination spans multiple configured data sources",
                    resource,
                    "requestedSchemas=" + schemas + ", dataSources=" + dataSources + ", mappings=" + schemaToDataSource
            );
        }
        return allow(
                "DATA_SCOPE_ALLOWED",
                "Cross-schema access is allowed for the current agent",
                resource,
                "requestedSchemas=" + schemas + ", dataSources=" + dataSources
        );
    }

    private List<DataScopeTarget> normalizeTargets(Collection<DataScopeTarget> targets) {
        if (targets == null || targets.isEmpty()) {
            return List.of(new DataScopeTarget("*", null));
        }
        List<DataScopeTarget> normalized = new ArrayList<>();
        for (DataScopeTarget target : targets) {
            if (target == null) {
                continue;
            }
            normalized.add(new DataScopeTarget(
                    normalizeSchema(target.schema()),
                    normalizeTable(target.table())
            ));
        }
        return normalized.isEmpty() ? List.of(new DataScopeTarget("*", null)) : normalized;
    }

    private String normalizeSchema(String schema) {
        return schema == null || schema.isBlank() ? "*" : schema.trim();
    }

    private String normalizeTable(String table) {
        return table == null || table.isBlank() ? null : table.trim();
    }

    private boolean matchesAllowedDataSource(List<String> rules, String dataSource) {
        for (String rule : rules) {
            if (matchesToken(dataSource, rule)) {
                return true;
            }
        }
        return false;
    }

    private List<String> buildSchemaPairs(Set<String> schemas) {
        List<String> values = new ArrayList<>(schemas);
        List<String> pairs = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            for (int j = i + 1; j < values.size(); j++) {
                pairs.add(canonicalSchemaPair(values.get(i), values.get(j)));
            }
        }
        return pairs;
    }

    private boolean matchesCrossSchemaRule(String requestedPair, List<String> rules) {
        for (String rule : rules) {
            if (rule == null || rule.isBlank()) {
                continue;
            }
            String normalized = rule.trim();
            if (WILDCARD.equals(normalized)) {
                return true;
            }
            String[] parts = normalized.split("[:>,|/]", 2);
            if (parts.length != 2) {
                continue;
            }
            if (requestedPair.equals(canonicalSchemaPair(parts[0].trim(), parts[1].trim()))) {
                return true;
            }
        }
        return false;
    }

    private String canonicalSchemaPair(String left, String right) {
        String normalizedLeft = normalizeSchema(left).toLowerCase();
        String normalizedRight = normalizeSchema(right).toLowerCase();
        return normalizedLeft.compareTo(normalizedRight) <= 0
                ? normalizedLeft + ":" + normalizedRight
                : normalizedRight + ":" + normalizedLeft;
    }

    private ToolAccessDecision allow(String reasonCode, String reasonMessage, String resource) {
        return allow(reasonCode, reasonMessage, resource, null);
    }

    private ToolAccessDecision allow(String reasonCode, String reasonMessage, String resource, String detail) {
        return ToolAccessDecision.builder()
                .allowed(true)
                .reasonCode(reasonCode)
                .reasonMessage(reasonMessage)
                .resource(resource)
                .detail(detail)
                .build();
    }

    private ToolAccessDecision deny(String reasonCode, String reasonMessage, String resource) {
        return deny(reasonCode, reasonMessage, resource, null);
    }

    private ToolAccessDecision deny(String reasonCode, String reasonMessage, String resource, String detail) {
        return ToolAccessDecision.builder()
                .allowed(false)
                .reasonCode(reasonCode)
                .reasonMessage(reasonMessage)
                .resource(resource)
                .detail(detail)
                .build();
    }

    private String buildDataScopeResource(String schema, Collection<String> tables) {
        if (tables == null || tables.isEmpty()) {
            return "data:" + schema + ".*";
        }
        return "data:" + schema + "." + String.join(",", tables);
    }

    private String buildDataScopeResource(Collection<DataScopeTarget> targets) {
        if (targets == null || targets.isEmpty()) {
            return "data:*.*";
        }
        List<String> resources = new ArrayList<>();
        for (DataScopeTarget target : targets) {
            resources.add(
                    "data:" + normalizeSchema(target.schema()) + "." + (target.table() == null ? "*" : target.table())
            );
        }
        return String.join("|", resources);
    }
}
