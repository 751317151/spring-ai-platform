package com.huah.ai.platform.agent.config;

import com.huah.ai.platform.agent.tools.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(10)
                .build();
    }

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

}
