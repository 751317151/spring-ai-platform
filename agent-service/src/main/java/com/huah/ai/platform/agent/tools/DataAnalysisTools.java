package com.huah.ai.platform.agent.tools;

import com.huah.ai.platform.agent.config.ToolsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 数据分析工具集 — JdbcTemplate 只读 SQL 执行 + 聚合统计
 * <p>
 * SQL 安全六层防护：
 * 1. SELECT/WITH 开头检查
 * 2. 分号禁止（防多语句注入）
 * 3. 危险关键词黑名单
 * 4. 注释禁止
 * 5. 自动追加 LIMIT
 * 6. 只读连接 + 超时
 */
@Slf4j
@Component
public class DataAnalysisTools {

    private final JdbcTemplate jdbcTemplate;
    private final ToolsProperties.DataAnalysisConfig config;

    private static final Set<String> DANGEROUS_KEYWORDS = Set.of(
            "DROP", "DELETE", "TRUNCATE", "ALTER", "CREATE", "INSERT",
            "UPDATE", "GRANT", "REVOKE", "EXEC", "EXECUTE", "CALL",
            "COPY", "VACUUM", "REINDEX", "CLUSTER"
    );

    public DataAnalysisTools(JdbcTemplate jdbcTemplate, ToolsProperties props) {
        this.jdbcTemplate = jdbcTemplate;
        this.config = props.getDataAnalysis();
    }

    @Tool(description = "执行只读 SQL 查询，返回业务数据库的查询结果。仅支持 SELECT 语句。")
    public Map<String, Object> executeQuery(
            @ToolParam(description = "要执行的 SQL 查询语句，仅支持 SELECT") String sql) {
        log.info("[Tool] executeQuery: sql={}", sql);
        try {
            String normalized = sql.trim().replaceAll("\\s+", " ");
            String upper = normalized.toUpperCase();

            // Safety Layer 1: SELECT/WITH only
            if (!upper.startsWith("SELECT") && !upper.startsWith("WITH")) {
                return Map.of("error", "只允许 SELECT 查询语句（或 WITH ... SELECT）");
            }

            // Safety Layer 2: Block semicolons (multi-statement)
            if (normalized.contains(";")) {
                return Map.of("error", "不允许多条 SQL 语句");
            }

            // Safety Layer 3: Block dangerous keywords
            for (String keyword : DANGEROUS_KEYWORDS) {
                if (upper.matches(".*\\b" + keyword + "\\b.*")) {
                    return Map.of("error", "SQL 包含不允许的关键词: " + keyword);
                }
            }

            // Safety Layer 4: Block comments
            if (normalized.contains("--") || normalized.contains("/*")) {
                return Map.of("error", "SQL 不允许包含注释");
            }

            // Safety Layer 5: Enforce LIMIT
            if (!upper.contains("LIMIT")) {
                normalized = normalized + " LIMIT " + config.getMaxRows();
            }

            // Safety Layer 6: Read-only connection + timeout
            final String finalSql = normalized;
            long startMs = System.currentTimeMillis();

            List<Map<String, Object>> rows = jdbcTemplate.execute(
                    (ConnectionCallback<List<Map<String, Object>>>) connection -> {
                        connection.setReadOnly(true);
                        connection.setAutoCommit(false);
                        try (var stmt = connection.createStatement()) {
                            stmt.setQueryTimeout(config.getQueryTimeoutSeconds());
                            var rs = stmt.executeQuery(finalSql);
                            var metaData = rs.getMetaData();
                            int colCount = metaData.getColumnCount();

                            List<String> columns = new ArrayList<>();
                            for (int i = 1; i <= colCount; i++) {
                                columns.add(metaData.getColumnLabel(i));
                            }

                            List<Map<String, Object>> resultRows = new ArrayList<>();
                            int rowCount = 0;
                            while (rs.next() && rowCount < config.getMaxRows()) {
                                Map<String, Object> row = new LinkedHashMap<>();
                                for (int i = 1; i <= colCount; i++) {
                                    row.put(columns.get(i - 1), rs.getObject(i));
                                }
                                resultRows.add(row);
                                rowCount++;
                            }
                            return resultRows;
                        } finally {
                            connection.rollback();
                            connection.setReadOnly(false);
                        }
                    });

            long elapsed = System.currentTimeMillis() - startMs;

            List<String> columns = (rows != null && !rows.isEmpty())
                    ? new ArrayList<>(rows.get(0).keySet())
                    : List.of();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("columns", columns);
            result.put("rows", rows != null ? rows : List.of());
            result.put("totalRows", rows != null ? rows.size() : 0);
            result.put("executionTime", elapsed + "ms");
            return result;
        } catch (Exception e) {
            log.error("[Tool] executeQuery failed: sql={}, error={}", sql, e.getMessage());
            return Map.of("error", "SQL 执行失败: " + e.getMessage());
        }
    }

    @Tool(description = "生成图表配置建议，支持柱状图、折线图、饼图等。请先用 executeQuery 获取数据，再调用此工具。")
    public Map<String, Object> generateChart(
            @ToolParam(description = "图表类型: bar|line|pie|scatter") String chartType,
            @ToolParam(description = "数据描述或来源") String data,
            @ToolParam(description = "图表标题") String title) {
        log.info("[Tool] generateChart: type={}, title={}", chartType, title);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("chartType", chartType);
        result.put("title", title);
        result.put("dataDescription", data);
        result.put("suggestion", "请先使用 executeQuery 工具获取实际数据，然后基于返回的数据生成前端可用的 ECharts 配置");
        result.put("templateUrl", "https://echarts.apache.org/examples");
        return result;
    }

    @Tool(description = "分析数据表的统计特征，包括记录数、均值、最小值、最大值、标准差等")
    public Map<String, Object> analyzeDataset(
            @ToolParam(description = "数据库表名") String datasetName,
            @ToolParam(description = "要分析的数值列名，逗号分隔") String metrics) {
        log.info("[Tool] analyzeDataset: dataset={}, metrics={}", datasetName, metrics);
        try {
            // Validate table name: alphanumeric + underscore only
            if (!datasetName.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
                return Map.of("error", "无效的表名: " + datasetName);
            }

            String[] columns = metrics.split(",");
            for (String col : columns) {
                if (!col.trim().matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
                    return Map.of("error", "无效的列名: " + col.trim());
                }
            }

            // Build aggregate SQL
            StringBuilder sqlBuilder = new StringBuilder("SELECT COUNT(*) AS record_count");
            for (String col : columns) {
                String c = col.trim();
                sqlBuilder.append(String.format(
                        ", AVG(%s) AS %s_avg, MIN(%s) AS %s_min, MAX(%s) AS %s_max, STDDEV(%s) AS %s_stddev",
                        c, c, c, c, c, c, c, c));
            }
            sqlBuilder.append(" FROM ").append(datasetName);

            String sql = sqlBuilder.toString();
            log.debug("[Tool] analyzeDataset SQL: {}", sql);

            Map<String, Object> stats = jdbcTemplate.queryForMap(sql);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("dataset", datasetName);
            result.put("recordCount", stats.get("record_count"));
            result.put("statistics", stats);
            return result;
        } catch (Exception e) {
            log.error("[Tool] analyzeDataset failed: dataset={}, error={}", datasetName, e.getMessage());
            return Map.of("error", "数据分析失败: " + e.getMessage());
        }
    }
}
