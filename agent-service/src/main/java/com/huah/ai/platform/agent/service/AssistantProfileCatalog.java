package com.huah.ai.platform.agent.service;

import com.huah.ai.platform.agent.config.AgentSystemPrompts;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AssistantProfileCatalog {

    public static final String GENERIC_PROFILE = "generic";
    public static final String MCP_PROFILE = "mcp";
    public static final String DEFAULT_MODEL = "auto";
    private static final String SYSTEM_OPERATOR = "system";

    private final Map<String, AssistantProfileDescriptor> profileDescriptors;
    private final List<BuiltInAssistantDefinition> builtInDefinitions;

    public AssistantProfileCatalog() {
        Map<String, AssistantProfileDescriptor> profiles = new LinkedHashMap<>();
        profiles.put(GENERIC_PROFILE, new AssistantProfileDescriptor(GENERIC_PROFILE, false, true, false, false));
        profiles.put(MCP_PROFILE, new AssistantProfileDescriptor(MCP_PROFILE, true, true, false, false));
        this.profileDescriptors = Map.copyOf(profiles);
        this.builtInDefinitions = List.of(
                normal("rd", "研发助手", "RD", "#4f8ef7", "研发问答与方案分析", AgentSystemPrompts.RD, 10),
                normal("sales", "销售助手", "SA", "#3dd68c", "销售支持与客户沟通", AgentSystemPrompts.SALES, 20),
                normal("hr", "HR 助手", "HR", "#9d7cf4", "人事制度与流程答疑", AgentSystemPrompts.HR, 30),
                normal("finance", "财务助手", "FN", "#f5a623", "财务分析与报表解读", AgentSystemPrompts.FINANCE, 40),
                normal("supply-chain", "供应链助手", "SC", "#2dd4bf", "供应链协同与状态跟踪", AgentSystemPrompts.SUPPLY_CHAIN, 50),
                normal("qc", "质控助手", "QC", "#f06060", "质量事件分析与预警", AgentSystemPrompts.QC, 60),
                normal("weather", "天气助手", "WX", "#38bdf8", "天气查询与出行建议", AgentSystemPrompts.WEATHER, 70),
                normal("search", "搜索助手", "SE", "#6366f1", "通用搜索与信息归纳", AgentSystemPrompts.SEARCH, 80),
                normal("data-analysis", "数据分析助手", "DA", "#f59e0b", "数据查询与分析说明", AgentSystemPrompts.DATA_ANALYSIS, 90),
                normal("code", "代码助手", "CO", "#10b981", "代码分析与实现建议", AgentSystemPrompts.CODE, 100),
                special("mcp", "MCP 助手", "MC", "#8b5cf6", "MCP 工具接入、服务诊断和能力扩展", AgentSystemPrompts.MCP, 110)
        );
    }

    public List<BuiltInAssistantDefinition> getBuiltInDefinitions() {
        return builtInDefinitions;
    }

    public boolean supportsProfile(String profileCode) {
        return profileDescriptors.containsKey(normalizeProfile(profileCode));
    }

    public String normalizeProfile(String profileCode) {
        if (profileCode == null) {
            return "";
        }
        return profileCode.trim().toLowerCase(Locale.ROOT);
    }

    public AssistantProfileDescriptor getRequiredProfile(String profileCode) {
        return Optional.ofNullable(profileDescriptors.get(normalizeProfile(profileCode)))
                .orElseThrow(() -> new IllegalArgumentException("Unsupported assistantProfile: " + profileCode));
    }

    public Set<String> supportedProfiles() {
        return profileDescriptors.keySet();
    }

    private BuiltInAssistantDefinition normal(
            String agentCode,
            String agentName,
            String icon,
            String color,
            String description,
            String systemPrompt,
            int sortOrder) {
        return new BuiltInAssistantDefinition(
                agentCode,
                agentName,
                description,
                icon,
                color,
                systemPrompt,
                DEFAULT_MODEL,
                defaultToolCodes(agentCode),
                "",
                true,
                sortOrder,
                100000,
                defaultRoles(agentCode),
                GENERIC_PROFILE,
                false,
                SYSTEM_OPERATOR,
                SYSTEM_OPERATOR);
    }

    private BuiltInAssistantDefinition special(
            String agentCode,
            String agentName,
            String icon,
            String color,
            String description,
            String systemPrompt,
            int sortOrder) {
        return new BuiltInAssistantDefinition(
                agentCode,
                agentName,
                description,
                icon,
                color,
                systemPrompt,
                DEFAULT_MODEL,
                "",
                "",
                true,
                sortOrder,
                100000,
                List.of("ROLE_ADMIN"),
                MCP_PROFILE,
                true,
                SYSTEM_OPERATOR,
                SYSTEM_OPERATOR);
    }

    private List<String> defaultRoles(String agentCode) {
        return switch (agentCode) {
            case "rd" -> List.of("ROLE_ADMIN", "ROLE_RD");
            case "sales" -> List.of("ROLE_ADMIN", "ROLE_SALES");
            case "hr" -> List.of("ROLE_ADMIN", "ROLE_HR");
            case "finance" -> List.of("ROLE_ADMIN", "ROLE_FINANCE");
            default -> List.of("ROLE_ADMIN", "ROLE_USER");
        };
    }

    private String defaultToolCodes(String agentCode) {
        return switch (agentCode) {
            case "rd" -> "rd-tools,internal-api";
            case "sales" -> "sales-tools";
            case "hr" -> "hr-tools";
            case "finance" -> "finance-tools";
            case "supply-chain" -> "supply-chain-tools";
            case "qc" -> "qc-tools";
            case "weather" -> "weather-tools";
            case "search" -> "search-tools";
            case "data-analysis" -> "data-analysis-tools";
            case "code" -> "code-tools";
            default -> "";
        };
    }

    public record AssistantProfileDescriptor(
            String profileCode,
            boolean supportsKnowledge,
            boolean supportsTools,
            boolean supportsMultiAgentMode,
            boolean supportsMultiStepRecovery) {
    }

    public record BuiltInAssistantDefinition(
            String agentCode,
            String agentName,
            String description,
            String icon,
            String color,
            String systemPrompt,
            String defaultModel,
            String toolCodes,
            String mcpServerCodes,
            Boolean enabled,
            Integer sortOrder,
            Integer dailyTokenLimit,
            List<String> allowedRoles,
            String assistantProfile,
            Boolean systemDefined,
            String createdBy,
            String updatedBy) {
    }
}
