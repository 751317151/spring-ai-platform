package com.huah.ai.platform.agent.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 企业内部工具集
 * 通过 @Tool 注解暴露给 AI Agent 使用
 */
@Slf4j
@Component
public class EnterpriseTools {

    // ==================== 研发工具 ====================

    @Tool(description = "查询 Jira 缺陷系统，获取指定项目的 Bug 列表或特定 Issue 详情")
    public Map<String, Object> queryJira(
            @ToolParam(description = "Jira 项目 Key，如 PROJ-123 或 PROJ") String projectOrIssue,
            @ToolParam(description = "过滤状态: TODO|IN_PROGRESS|DONE，可为空") String status) {
        log.info("[Tool] queryJira: project={}, status={}", projectOrIssue, status);
        // TODO: 调用真实 Jira REST API
        // JiraClient.getIssues(projectOrIssue, status)
        return Map.of(
                "issues", List.of(
                        Map.of("key", "PROJ-101", "summary", "登录接口性能问题", "status", "IN_PROGRESS", "priority", "High"),
                        Map.of("key", "PROJ-102", "summary", "Redis 连接池泄漏", "status", "TODO", "priority", "Critical")
                ),
                "total", 2
        );
    }

    @Tool(description = "查询 Confluence 技术文档，按关键词搜索相关文档")
    public List<Map<String, String>> queryConfluence(
            @ToolParam(description = "搜索关键词") String keyword,
            @ToolParam(description = "文档空间 Key，如 TECH、ARCH，可为空") String spaceKey) {
        log.info("[Tool] queryConfluence: keyword={}, space={}", keyword, spaceKey);
        // TODO: 调用 Confluence REST API
        return List.of(
                Map.of("title", "微服务架构设计规范", "url", "http://confluence/TECH/arch-spec", "space", "TECH"),
                Map.of("title", "数据库设计规范", "url", "http://confluence/TECH/db-spec", "space", "TECH")
        );
    }

    @Tool(description = "查询 SonarQube 代码质量报告，获取指定项目的质量指标")
    public Map<String, Object> querySonar(
            @ToolParam(description = "SonarQube 项目 Key") String projectKey) {
        log.info("[Tool] querySonar: project={}", projectKey);
        return Map.of(
                "projectKey", projectKey,
                "bugs", 3,
                "vulnerabilities", 1,
                "codeSmells", 45,
                "coverage", "72.5%",
                "duplications", "8.3%",
                "qualityGate", "WARN"
        );
    }

    // ==================== 生产质控工具 ====================

    @Tool(description = "查询生产质控数据，获取指定时间段内的不良品率和质检报告")
    public Map<String, Object> queryQualityControl(
            @ToolParam(description = "生产线编号") String lineId,
            @ToolParam(description = "开始日期，格式 yyyy-MM-dd") String startDate,
            @ToolParam(description = "结束日期，格式 yyyy-MM-dd") String endDate) {
        log.info("[Tool] queryQC: line={}, start={}, end={}", lineId, startDate, endDate);
        return Map.of(
                "lineId", lineId,
                "period", startDate + " ~ " + endDate,
                "totalProduced", 10000,
                "defects", 120,
                "defectRate", "1.2%",
                "topDefects", List.of(
                        Map.of("type", "外观瑕疵", "count", 60, "ratio", "50%"),
                        Map.of("type", "尺寸偏差", "count", 40, "ratio", "33%")
                )
        );
    }

    // ==================== 销售报价工具 ====================

    @Tool(description = "查询产品报价，获取指定产品的当前报价和历史价格趋势")
    public Map<String, Object> queryProductPrice(
            @ToolParam(description = "产品编号或产品名称") String productId,
            @ToolParam(description = "客户级别: A|B|C，影响折扣，可为空默认标准价") String customerLevel) {
        log.info("[Tool] queryPrice: product={}, customer={}", productId, customerLevel);
        double basePrice = 1999.0;
        double discount = "A".equals(customerLevel) ? 0.85 : "B".equals(customerLevel) ? 0.9 : 0.95;
        return Map.of(
                "productId", productId,
                "productName", "智能传感器 Model X",
                "listPrice", basePrice,
                "customerLevel", customerLevel,
                "discount", discount,
                "finalPrice", basePrice * discount,
                "currency", "CNY",
                "validUntil", LocalDate.now().plusDays(30).toString()
        );
    }

    // ==================== HR/行政工具 ====================

    @Tool(description = "查询员工信息，包括部门、职级、在职状态")
    public Map<String, Object> queryEmployee(
            @ToolParam(description = "员工工号或姓名") String employeeIdOrName) {
        log.info("[Tool] queryEmployee: {}", employeeIdOrName);
        return Map.of(
                "employeeId", "EMP001",
                "name", "张三",
                "department", "研发中心",
                "position", "高级工程师",
                "level", "P6",
                "status", "ACTIVE",
                "manager", "李四"
        );
    }

    @Tool(description = "提交审批申请，如请假、报销、采购等，返回审批单号")
    public Map<String, String> submitApproval(
            @ToolParam(description = "审批类型: LEAVE|EXPENSE|PURCHASE|OTHER") String type,
            @ToolParam(description = "申请内容 JSON 字符串") String content,
            @ToolParam(description = "申请人工号") String applicantId) {
        log.info("[Tool] submitApproval: type={}, applicant={}", type, applicantId);
        String approvalNo = "APV-" + System.currentTimeMillis();
        // TODO: 对接 OA 审批系统
        return Map.of(
                "approvalNo", approvalNo,
                "status", "SUBMITTED",
                "message", "审批申请已提交，审批单号: " + approvalNo
        );
    }

    // ==================== 财务工具 ====================

    @Tool(description = "查询财务报表，获取指定部门或整体的收支数据")
    public Map<String, Object> queryFinancialReport(
            @ToolParam(description = "报表类型: INCOME|EXPENSE|BALANCE") String reportType,
            @ToolParam(description = "年份，如 2024") String year,
            @ToolParam(description = "月份 1-12，可为空表示全年") String month) {
        log.info("[Tool] queryFinance: type={}, year={}, month={}", reportType, year, month);
        return Map.of(
                "reportType", reportType,
                "period", year + (month != null ? "-" + month : ""),
                "revenue", 12500000,
                "expense", 9800000,
                "profit", 2700000,
                "profitRate", "21.6%",
                "currency", "CNY"
        );
    }

    // ==================== 供应链工具 ====================

    @Tool(description = "查询库存状态，获取指定物料或产品的当前库存数量")
    public Map<String, Object> queryInventory(
            @ToolParam(description = "物料编号或产品编号") String materialId,
            @ToolParam(description = "仓库编号，可为空查询所有仓库") String warehouseId) {
        log.info("[Tool] queryInventory: material={}, warehouse={}", materialId, warehouseId);
        return Map.of(
                "materialId", materialId,
                "materialName", "高精度传感器芯片",
                "totalStock", 5000,
                "availableStock", 3200,
                "reservedStock", 1800,
                "unit", "个",
                "safetyStock", 1000,
                "reorderPoint", 1500,
                "status", "SUFFICIENT"
        );
    }

    @Tool(description = "查询采购订单状态")
    public Map<String, Object> queryPurchaseOrder(
            @ToolParam(description = "采购订单号，如 PO-2024-001，或供应商名称") String orderOrSupplier) {
        log.info("[Tool] queryPO: {}", orderOrSupplier);
        return Map.of(
                "orderId", "PO-2024-001",
                "supplier", "ABC电子器件有限公司",
                "status", "IN_TRANSIT",
                "expectedDelivery", LocalDate.now().plusDays(7).toString(),
                "items", List.of(
                        Map.of("material", "传感器芯片", "quantity", 2000, "unitPrice", 45.5)
                ),
                "totalAmount", 91000
        );
    }
}
