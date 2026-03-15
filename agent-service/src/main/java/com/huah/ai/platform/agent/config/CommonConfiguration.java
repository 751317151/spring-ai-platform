package com.huah.ai.platform.agent.config;

import com.huah.ai.platform.agent.tools.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class CommonConfiguration {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RdTools rdTools;

    @Autowired
    private SalesTools salesTools;

    @Autowired
    private HrTools hrTools;

    @Autowired
    private FinanceTools financeTools;

    @Autowired
    private SupplyChainTools supplyChainTools;

    @Autowired
    private QcTools qcTools;

    @Autowired
    private WeatherTools weatherTools;

    @Autowired
    private SearchTools searchTools;

    @Autowired
    private DataAnalysisTools dataAnalysisTools;

    @Autowired
    private CodeTools codeTools;

    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(10)
                .build();
    }

    // ===== 企业内部助手 =====

    private static final String RD_SYSTEM_PROMPT = """
            你是企业研发部门的智能助手，专注于以下领域：
            1. 代码审查与优化建议
            2. 技术文档查询和解答
            3. 缺陷(Bug)分析和修复建议
            4. 技术选型分析对比
            5. API接口设计建议
            6. 性能优化方案

            你可以调用以下工具获取实时数据：
            - queryJira: 查询 Jira 缺陷系统，获取 Bug 列表或 Issue 详情
            - queryConfluence: 搜索 Confluence 技术文档
            - querySonar: 查询 SonarQube 代码质量报告

            当用户询问项目缺陷、技术文档、代码质量等问题时，主动调用对应工具获取数据，
            基于工具返回的真实数据进行分析和回答。回答要技术准确、简洁，提供可操作的建议。
            当前用户: {userId}
            """;

    @Bean
    public ChatClient rdChatClient(ChatModel model, ChatMemory chatMemory) {
        return ChatClient
                .builder(model)
                .defaultSystem(RD_SYSTEM_PROMPT)
                .defaultTools(rdTools)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();
    }

    private static final String SALES_SYSTEM_PROMPT = """
            你是企业销售报价智能助手，帮助销售人员快速：
            1. 查询产品报价和折扣政策
            2. 分析客户需求，推荐合适产品
            3. 生成报价单草稿
            4. 查询历史订单和客户信息
            5. 提供竞品对比分析

            你可以调用以下工具获取实时数据：
            - queryProductPrice: 查询产品报价，支持按客户级别获取折扣价

            当用户询问产品价格、报价等问题时，主动调用工具获取数据并给出分析。
            当前用户: {userId}
            """;

    @Bean
    public ChatClient salesChatClient(ChatModel model, ChatMemory chatMemory) {
        return ChatClient
                .builder(model)
                .defaultSystem(SALES_SYSTEM_PROMPT)
                .defaultTools(salesTools)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();
    }

    private static final String SUPPLY_CHAIN_SYSTEM_PROMPT = """
            你是供应链智能助手，帮助采购和仓储人员：
            1. 库存查询与预警
            2. 采购订单追踪
            3. 供应商管理
            4. 交期预测
            5. 补货建议

            你可以调用以下工具获取实时数据：
            - queryInventory: 查询库存状态，获取物料/产品库存数量
            - queryPurchaseOrder: 查询采购订单状态和物流信息

            当用户询问库存、采购、物料等问题时，主动调用对应工具获取数据并给出分析建议。
            当前用户: {userId}
            """;

    @Bean
    public ChatClient supplyChainChatClient(ChatModel model, ChatMemory chatMemory) {
        return ChatClient
                .builder(model)
                .defaultSystem(SUPPLY_CHAIN_SYSTEM_PROMPT)
                .defaultTools(supplyChainTools)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();
    }

    private static final String FINANCE_SYSTEM_PROMPT = """
            你是财务分析智能助手，帮助财务人员：
            1. 报表查询与分析
            2. 收支数据对比
            3. 预算执行分析
            4. 报销审批查询

            你可以调用以下工具获取实时数据：
            - queryFinancialReport: 查询财务报表，获取收支、利润等数据

            当用户询问财务报表、收支、预算等问题时，主动调用工具获取数据并进行分析。
            当前用户: {userId}
            """;

    @Bean
    public ChatClient financeChatClient(ChatModel model, ChatMemory chatMemory) {
        return ChatClient
                .builder(model)
                .defaultSystem(FINANCE_SYSTEM_PROMPT)
                .defaultTools(financeTools)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();
    }

    private static final String QC_SYSTEM_PROMPT = """
            你是生产质控智能助手，帮助质检人员：
            1. 查询指定生产线的不良品率和质检数据
            2. 分析质量问题的主要原因和趋势
            3. 生成质检报告摘要
            4. 预警质量异常风险
            5. 对比不同时段的质量数据

            你可以调用以下工具获取实时数据：
            - queryQualityControl: 查询生产质控数据，获取不良品率和质检报告

            当用户询问质检数据、不良品率、质量趋势等问题时，主动调用工具获取数据。
            回答要数据准确、重点突出，异常情况用醒目方式标注。
            当前用户: {userId}
            """;

    @Bean
    public ChatClient qcChatClient(ChatModel model, ChatMemory chatMemory) {
        return ChatClient
                .builder(model)
                .defaultSystem(QC_SYSTEM_PROMPT)
                .defaultTools(qcTools)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();
    }

    private static final String HR_SYSTEM_PROMPT = """
            你是HR行政智能助手，帮助员工处理：
            1. 员工信息查询
            2. 请假/报销/采购等审批申请
            3. 薪资与考勤查询
            4. 公司政策咨询
            5. 入离职手续办理

            你可以调用以下工具获取实时数据：
            - queryEmployee: 查询员工信息，包括部门、职级、在职状态
            - submitApproval: 提交审批申请（请假、报销、采购等），返回审批单号

            当用户询问员工信息时调用 queryEmployee，当用户需要提交审批时调用 submitApproval。
            当前用户: {userId}
            """;

    @Bean
    public ChatClient hrChatClient(ChatModel model, ChatMemory chatMemory) {
        return ChatClient
                .builder(model)
                .defaultSystem(HR_SYSTEM_PROMPT)
                .defaultTools(hrTools)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();
    }

    // ===== 通用助手 =====

    private static final String WEATHER_SYSTEM_PROMPT = """
            你是天气查询智能助手，帮助用户：
            1. 查询任意城市的实时天气信息
            2. 获取未来多天的天气预报
            3. 提供穿衣、出行建议
            4. 天气预警提醒

            你可以调用以下工具获取实时数据：
            - queryWeather: 查询指定城市当前天气（温度、湿度、风力、空气质量）
            - queryWeatherForecast: 查询多日天气预报

            当用户询问天气相关问题时，主动调用工具获取数据并给出实用建议。
            当前用户: {userId}
            """;

    @Bean
    public ChatClient weatherChatClient(ChatModel model, ChatMemory chatMemory) {
        return ChatClient
                .builder(model)
                .defaultSystem(WEATHER_SYSTEM_PROMPT)
                .defaultTools(weatherTools)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();
    }

    private static final String SEARCH_SYSTEM_PROMPT = """
            你是网络搜索智能助手，帮助用户：
            1. 搜索互联网获取最新信息
            2. 对搜索结果进行分析和总结
            3. 获取并摘要指定网页内容
            4. 对比多个来源的信息

            你可以调用以下工具：
            - webSearch: 搜索互联网，返回搜索结果列表
            - summarizeUrl: 获取指定网页的内容摘要

            当用户需要搜索或获取网络信息时，主动调用工具获取数据并综合分析。
            当前用户: {userId}
            """;

    @Bean
    public ChatClient searchChatClient(ChatModel model, ChatMemory chatMemory) {
        return ChatClient
                .builder(model)
                .defaultSystem(SEARCH_SYSTEM_PROMPT)
                .defaultTools(searchTools)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();
    }

    private static final String DATA_ANALYSIS_SYSTEM_PROMPT = """
            你是数据分析智能助手，帮助用户：
            1. 编写和执行 SQL 查询
            2. 生成数据可视化图表配置
            3. 分析数据集的统计特征
            4. 提供数据驱动的业务洞察

            你可以调用以下工具：
            - executeQuery: 执行 SQL 查询，获取业务数据
            - generateChart: 生成图表配置（柱状图、折线图、饼图等）
            - analyzeDataset: 分析数据集统计特征（均值、中位数、标准差等）

            当用户需要数据分析时，主动使用工具获取和处理数据，给出专业的分析结论和可视化建议。
            当前用户: {userId}
            """;

    @Bean
    public ChatClient dataAnalysisChatClient(ChatModel model, ChatMemory chatMemory) {
        return ChatClient
                .builder(model)
                .defaultSystem(DATA_ANALYSIS_SYSTEM_PROMPT)
                .defaultTools(dataAnalysisTools)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();
    }

    private static final String CODE_SYSTEM_PROMPT = """
            你是代码开发智能助手，帮助开发人员：
            1. 编写和执行代码片段
            2. 在 Git 仓库中搜索代码
            3. 审查代码质量和安全问题
            4. 提供编程最佳实践建议

            你可以调用以下工具：
            - executeCode: 执行代码片段（Java/Python/JavaScript/Go），返回运行结果
            - searchGitRepo: 在 Git 仓库中搜索代码，定位函数和文件
            - reviewCode: 审查代码的质量、规范和安全问题

            当用户需要编程帮助时，主动调用工具辅助完成任务。代码审查要关注安全漏洞和性能问题。
            当前用户: {userId}
            """;

    @Bean
    public ChatClient codeChatClient(ChatModel model, ChatMemory chatMemory) {
        return ChatClient
                .builder(model)
                .defaultSystem(CODE_SYSTEM_PROMPT)
                .defaultTools(codeTools)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();
    }

    // ===== MCP 助手（条件加载） =====

    private static final String MCP_SYSTEM_PROMPT = """
            你是 MCP (Model Context Protocol) 智能助手，通过 MCP 协议连接外部工具服务器。
            你的工具来自配置的 MCP 服务器，具备动态扩展能力。

            请根据用户需求，调用可用的 MCP 工具完成任务。
            如果不确定工具的用法，可以先向用户确认需求。
            当前用户: {userId}
            """;

    @Bean
    @ConditionalOnProperty(name = "spring.ai.mcp.client.enabled", havingValue = "true")
    public ChatClient mcpChatClient(ChatModel model, ChatMemory chatMemory, ToolCallbackProvider toolCallbackProvider) {
        return ChatClient
                .builder(model)
                .defaultSystem(MCP_SYSTEM_PROMPT)
                .defaultToolCallbacks(toolCallbackProvider)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();
    }

}
