package com.huah.ai.platform.agent.service;

import com.huah.ai.platform.agent.dto.AgentMetadataItem;
import com.huah.ai.platform.agent.dto.AgentMetadataResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AgentMetadataService {

    private static final String DEFAULT_MODEL = "auto";
    private static final double DEFAULT_TEMPERATURE = 0.7d;
    private static final int DEFAULT_MAX_CONTEXT_MESSAGES = 10;

    private final AssistantAgentRegistry assistantAgentRegistry;

    public AgentMetadataService(AssistantAgentRegistry assistantAgentRegistry) {
        this.assistantAgentRegistry = assistantAgentRegistry;
    }

    public AgentMetadataResponse list() {
        Set<String> registeredTypes = assistantAgentRegistry.getRegisteredAgentTypes();
        List<AgentMetadataItem> agents = new ArrayList<>();
        metadataTemplates().forEach((agentType, item) -> {
            boolean registered = "multi".equals(agentType) || registeredTypes.contains(agentType);
            agents.add(AgentMetadataItem.builder()
                    .agentType(item.getAgentType())
                    .name(item.getName())
                    .icon(item.getIcon())
                    .color(item.getColor())
                    .description(item.getDescription())
                    .defaultModel(item.getDefaultModel())
                    .defaultTemperature(item.getDefaultTemperature())
                    .defaultMaxContextMessages(item.getDefaultMaxContextMessages())
                    .supportsKnowledge(item.isSupportsKnowledge())
                    .supportsTools(item.isSupportsTools())
                    .supportsMultiAgentMode(item.isSupportsMultiAgentMode())
                    .supportsMultiStepRecovery(item.isSupportsMultiStepRecovery())
                    .registered(registered)
                    .build());
        });
        return AgentMetadataResponse.builder()
                .count(agents.size())
                .agents(agents)
                .build();
    }

    private Map<String, AgentMetadataItem> metadataTemplates() {
        Map<String, AgentMetadataItem> items = new LinkedHashMap<>();
        items.put("rd", metadata("rd", "研发助手", "RD", "#4f8ef7", "代码审查、技术方案和缺陷分析", true, true, false, false));
        items.put("sales", metadata("sales", "销售助手", "SA", "#3dd68c", "报价查询、客户分析和商机建议", true, true, false, false));
        items.put("hr", metadata("hr", "HR 助手", "HR", "#9d7cf4", "假期制度、审批状态和人事问答", true, true, false, false));
        items.put("finance", metadata("finance", "财务助手", "FN", "#f5a623", "费用分析、报表解读和预算对比", true, true, false, false));
        items.put("supply-chain", metadata("supply-chain", "供应链助手", "SC", "#2dd4bf", "库存、采购和补货追踪", true, true, false, false));
        items.put("qc", metadata("qc", "质控助手", "QC", "#f06060", "质量事件、质检报告和风险预警", true, true, false, false));
        items.put("weather", metadata("weather", "天气助手", "WX", "#38bdf8", "天气查询、预报和出行建议", false, true, false, false));
        items.put("search", metadata("search", "搜索助手", "SE", "#6366f1", "互联网搜索、网页摘要和信息汇总", true, true, false, false));
        items.put("data-analysis", metadata("data-analysis", "数据分析助手", "DA", "#f59e0b", "数据查询、图表生成和统计分析", true, true, false, false));
        items.put("code", metadata("code", "代码助手", "CO", "#10b981", "仓库检索、代码分析和实现审查", true, true, false, false));
        items.put("mcp", metadata("mcp", "MCP 助手", "MC", "#8b5cf6", "MCP 工具接入、服务诊断和能力扩展", true, true, false, false));
        items.put("multi", metadata("multi", "多智能体", "MA", "#f97316", "复杂任务拆分、多助手协同和结果汇总", true, true, true, false));
        return items;
    }

    private AgentMetadataItem metadata(String agentType,
                                       String name,
                                       String icon,
                                       String color,
                                       String description,
                                       boolean supportsKnowledge,
                                       boolean supportsTools,
                                       boolean supportsMultiAgentMode,
                                       boolean supportsMultiStepRecovery) {
        return AgentMetadataItem.builder()
                .agentType(agentType)
                .name(name)
                .icon(icon)
                .color(color)
                .description(description)
                .defaultModel(DEFAULT_MODEL)
                .defaultTemperature(DEFAULT_TEMPERATURE)
                .defaultMaxContextMessages(DEFAULT_MAX_CONTEXT_MESSAGES)
                .supportsKnowledge(supportsKnowledge)
                .supportsTools(supportsTools)
                .supportsMultiAgentMode(supportsMultiAgentMode)
                .supportsMultiStepRecovery(supportsMultiStepRecovery)
                .registered(false)
                .build();
    }
}
