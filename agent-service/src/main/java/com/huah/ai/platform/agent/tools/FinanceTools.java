package com.huah.ai.platform.agent.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 财务领域工具集
 */
@Slf4j
@Component
public class FinanceTools {

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
}
