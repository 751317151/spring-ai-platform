package com.huah.ai.platform.agent.service;

import com.huah.ai.platform.agent.config.ToolsProperties;
import com.huah.ai.platform.agent.dto.AgentAccessOverviewResponse;
import com.huah.ai.platform.agent.dto.AgentAccessRuleItem;
import com.huah.ai.platform.agent.dto.McpServerInfo;
import com.huah.ai.platform.agent.security.ToolAccessDecision;
import com.huah.ai.platform.agent.security.ToolSecurityService;
import com.huah.ai.platform.agent.tools.InternalApiTools;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AgentAccessOverviewService {

    private static final String WILDCARD = "*";

    private final ToolSecurityService toolSecurityService;
    private final ToolsProperties toolsProperties;
    private final InternalApiTools internalApiTools;
    private final McpServerCatalogService mcpServerCatalogService;
    private final AgentRuntimePolicyService agentRuntimePolicyService;

    public AgentAccessOverviewResponse build(String agentType) {
        List<AgentAccessRuleItem> toolItems = buildToolItems(agentType);
        List<AgentAccessRuleItem> connectorItems = buildConnectorItems(agentType);
        List<AgentAccessRuleItem> mcpItems = buildMcpItems(agentType);

        String summary = "tools=" + toolItems.size()
                + ", connectors=" + connectorItems.size()
                + ", mcp=" + mcpItems.size()
                + ", security=" + (toolSecurityService.isSecurityEnabled() ? "on" : "off");

        return AgentAccessOverviewResponse.builder()
                .agentType(agentType)
                .securityEnabled(toolSecurityService.isSecurityEnabled())
                .runtimePolicySummary(agentRuntimePolicyService.build(agentType))
                .tools(toolItems)
                .connectors(connectorItems)
                .mcpServers(mcpItems)
                .summary(summary)
                .build();
    }

    private List<AgentAccessRuleItem> buildToolItems(String agentType) {
        List<String> allowedTools = toolSecurityService.getAllowedTools(agentType);
        if (!toolSecurityService.isSecurityEnabled()) {
            return List.of(toRuleItem(
                    "tool",
                    WILDCARD,
                    "全部工具",
                    true,
                    true,
                    "open",
                    ToolAccessDecision.builder()
                            .allowed(true)
                            .reasonCode("SECURITY_DISABLED")
                            .reasonMessage("工具安全控制未启用")
                            .resource("tool:*")
                            .build(),
                    "当前未启用工具安全控制，工具是否可用由 Spring AI 工具装配结果决定。"
            ));
        }
        if (allowedTools.isEmpty()) {
            return List.of(toRuleItem(
                    "tool",
                    "default",
                    "默认策略",
                    true,
                    true,
                    "fallback",
                    ToolAccessDecision.builder()
                            .allowed(true)
                            .reasonCode("TOOL_FALLBACK")
                            .reasonMessage("未配置工具白名单，当前按默认策略放行")
                            .resource("tool:*")
                            .build(),
                    "当前助手没有显式工具白名单，后端按默认策略处理。"
            ));
        }
        if (allowedTools.contains(WILDCARD)) {
            return List.of(toRuleItem(
                    "tool",
                    WILDCARD,
                    "全部工具",
                    true,
                    true,
                    "allowed",
                    ToolAccessDecision.builder()
                            .allowed(true)
                            .reasonCode("TOOL_ALLOWED")
                            .reasonMessage("命中工具通配白名单")
                            .resource("tool:*")
                            .build(),
                    "当前助手可访问全部工具。"
            ));
        }
        return allowedTools.stream()
                .map(tool -> toRuleItem(
                        "tool",
                        tool,
                        tool,
                        true,
                        true,
                        "allowed",
                        toolSecurityService.decideToolAccess(agentType, tool),
                        "当前助手允许调用该工具。"
                ))
                .toList();
    }

    private List<AgentAccessRuleItem> buildConnectorItems(String agentType) {
        Map<String, ToolsProperties.ConnectorDefinition> connectors = toolsProperties.getInternalApi().getConnectors();
        List<String> enabledConnectors = internalApiTools.listEnabledConnectorCodes();
        if (connectors == null || connectors.isEmpty()) {
            return List.of();
        }
        List<AgentAccessRuleItem> items = new ArrayList<>();
        for (Map.Entry<String, ToolsProperties.ConnectorDefinition> entry : connectors.entrySet()) {
            String code = entry.getKey();
            ToolsProperties.ConnectorDefinition definition = entry.getValue();
            boolean enabled = definition != null && definition.isEnabled() && enabledConnectors.contains(code);
            ToolAccessDecision accessDecision = toolSecurityService.decideConnectorAccess(agentType, code);
            List<String> resourcePrefixes = toolSecurityService.getAllowedConnectorResourcePrefixes(
                    agentType,
                    code,
                    definition != null ? definition.getAllowedPathPrefixes() : List.of()
            );
            String detail = "baseUrl=" + (definition != null ? nullToDash(definition.getBaseUrl()) : "-")
                    + ", paths=" + joinValues(definition != null ? definition.getAllowedPathPrefixes() : List.of())
                    + ", resources=" + joinValues(resourcePrefixes);
            items.add(toRuleItem(
                    "connector",
                    code,
                    definition != null && definition.getName() != null && !definition.getName().isBlank() ? definition.getName() : code,
                    enabled,
                    accessDecision.isAllowed(),
                    !enabled ? "disabled" : accessDecision.isAllowed() ? "allowed" : "denied",
                    accessDecision,
                    detail
            ));
        }
        return items;
    }

    private List<AgentAccessRuleItem> buildMcpItems(String agentType) {
        return mcpServerCatalogService.listAllServers(agentType).stream()
                .map(item -> toMcpRuleItem(agentType, item))
                .toList();
    }

    private AgentAccessRuleItem toMcpRuleItem(String agentType, McpServerInfo item) {
        boolean enabled = item.isEnabled() && item.isClientEnabled();
        ToolAccessDecision accessDecision = toolSecurityService.decideMcpServerAccess(agentType, item.getCode());
        String status;
        if (!enabled) {
            status = "disabled";
        } else if (!accessDecision.isAllowed()) {
            status = "denied";
        } else if (!"ready".equals(item.getDiagnosticStatus())) {
            status = "issue";
        } else {
            status = "allowed";
        }
        String detail = "command=" + nullToDash(item.getCommandLinePreview())
                + ", tools=" + joinValues(item.getAuthorizedTools());
        return toRuleItem(
                "mcp",
                item.getCode(),
                item.getCode(),
                enabled,
                accessDecision.isAllowed(),
                status,
                accessDecision,
                detail + ", hint=" + nullToDash(item.getRuntimeHint())
        );
    }

    private AgentAccessRuleItem toRuleItem(String category,
                                           String code,
                                           String name,
                                           boolean enabled,
                                           boolean authorized,
                                           String status,
                                           ToolAccessDecision decision,
                                           String detail) {
        return AgentAccessRuleItem.builder()
                .code(code)
                .name(name)
                .category(category)
                .enabled(enabled)
                .authorized(authorized)
                .status(status)
                .reason(decision != null ? decision.getReasonMessage() : null)
                .reasonCode(decision != null ? decision.getReasonCode() : null)
                .reasonMessage(decision != null ? decision.getReasonMessage() : null)
                .resource(decision != null ? decision.getResource() : null)
                .detail(detail)
                .build();
    }

    private String joinValues(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return "-";
        }
        return String.join(" | ", values);
    }

    private String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
