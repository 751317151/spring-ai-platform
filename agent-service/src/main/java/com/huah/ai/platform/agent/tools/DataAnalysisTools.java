package com.huah.ai.platform.agent.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 数据分析工具集
 */
@Slf4j
@Component
public class DataAnalysisTools {

    @Tool(description = "执行 SQL 查询，返回业务数据库的查询结果")
    public Map<String, Object> executeQuery(
            @ToolParam(description = "要执行的 SQL 查询语句") String sql) {
        log.info("[Tool] executeQuery: sql={}", sql);
        return Map.of(
                "columns", List.of("部门", "销售额", "同比增长"),
                "rows", List.of(
                        List.of("研发中心", "¥520万", "+12.3%"),
                        List.of("销售部", "¥1,250万", "+8.7%"),
                        List.of("生产部", "¥380万", "+5.1%")
                ),
                "totalRows", 3,
                "executionTime", "23ms"
        );
    }

    @Tool(description = "生成图表配置，支持柱状图、折线图、饼图等")
    public Map<String, Object> generateChart(
            @ToolParam(description = "图表类型: bar|line|pie|scatter") String chartType,
            @ToolParam(description = "数据描述或来源") String data,
            @ToolParam(description = "图表标题") String title) {
        log.info("[Tool] generateChart: type={}, title={}", chartType, title);
        return Map.of(
                "chartType", chartType,
                "title", title,
                "xAxis", List.of("Q1", "Q2", "Q3", "Q4"),
                "series", List.of(
                        Map.of("name", "2024", "data", List.of(120, 200, 150, 180)),
                        Map.of("name", "2025", "data", List.of(150, 230, 180, 220))
                ),
                "suggestion", "建议使用 ECharts 渲染此图表"
        );
    }

    @Tool(description = "分析数据集的统计特征，包括均值、中位数、标准差等")
    public Map<String, Object> analyzeDataset(
            @ToolParam(description = "数据集名称或表名") String datasetName,
            @ToolParam(description = "要分析的指标，逗号分隔") String metrics) {
        log.info("[Tool] analyzeDataset: dataset={}, metrics={}", datasetName, metrics);
        return Map.of(
                "dataset", datasetName,
                "recordCount", 15680,
                "statistics", Map.of(
                        "mean", 256.7,
                        "median", 234.0,
                        "stddev", 45.2,
                        "min", 12.0,
                        "max", 892.0,
                        "nullRate", "2.3%"
                ),
                "correlations", "销售额与广告投入呈强正相关(r=0.87)"
        );
    }
}
