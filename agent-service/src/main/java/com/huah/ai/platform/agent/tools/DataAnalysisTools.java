package com.huah.ai.platform.agent.tools;

import com.huah.ai.platform.agent.config.ToolsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.DatabaseMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class DataAnalysisTools {

    private static final Set<String> DANGEROUS_KEYWORDS = Set.of(
            "DROP", "DELETE", "TRUNCATE", "ALTER", "CREATE", "INSERT",
            "UPDATE", "GRANT", "REVOKE", "EXEC", "EXECUTE", "CALL",
            "COPY", "VACUUM", "REINDEX", "CLUSTER"
    );

    private static final Pattern TABLE_REFERENCE_PATTERN = Pattern.compile(
            "\\b(?:FROM|JOIN)\\s+([a-zA-Z_][a-zA-Z0-9_\\.]*)(?:\\s|$)",
            Pattern.CASE_INSENSITIVE
    );

    private final JdbcTemplate jdbcTemplate;
    private final ToolsProperties.DataAnalysisConfig config;

    public DataAnalysisTools(JdbcTemplate jdbcTemplate, ToolsProperties props) {
        this.jdbcTemplate = jdbcTemplate;
        this.config = props.getDataAnalysis();
    }

    @Tool(description = "列出当前只读 SQL 助手可访问的数据表，帮助用户先了解库表范围后再写查询。")
    public Map<String, Object> listAccessibleTables() {
        log.info("[Tool] listAccessibleTables");
        try {
            String schema = resolveSchema();
            List<Map<String, Object>> tables = jdbcTemplate.execute((ConnectionCallback<List<Map<String, Object>>>) connection -> {
                DatabaseMetaData metaData = connection.getMetaData();
                List<Map<String, Object>> rows = new ArrayList<>();
                try (var rs = metaData.getTables(null, schema, "%", new String[]{"TABLE", "VIEW"})) {
                    while (rs.next()) {
                        String tableName = rs.getString("TABLE_NAME");
                        if (!isTableAllowed(tableName)) {
                            continue;
                        }
                        rows.add(Map.of(
                                "schema", rs.getString("TABLE_SCHEM"),
                                "table", tableName,
                                "type", rs.getString("TABLE_TYPE")
                        ));
                    }
                }
                return rows;
            });
            return Map.of(
                    "schema", schema,
                    "tables", tables,
                    "count", tables.size()
            );
        } catch (Exception e) {
            log.error("[Tool] listAccessibleTables failed: {}", e.getMessage(), e);
            return Map.of("error", "列出数据表失败: " + e.getMessage());
        }
    }

    @Tool(description = "查看指定数据表的字段定义，返回字段名、类型、是否可空和备注，帮助编写正确 SQL。")
    public Map<String, Object> describeTable(
            @ToolParam(description = "要查看的数据表名，仅支持当前白名单内的数据表") String tableName) {
        log.info("[Tool] describeTable: table={}", tableName);
        try {
            String normalizedTable = normalizeSimpleIdentifier(tableName, "表名");
            if (!isTableAllowed(normalizedTable)) {
                return Map.of("error", "当前表不在只读助手允许范围内: " + normalizedTable);
            }

            String schema = resolveSchema();
            List<Map<String, Object>> columns = jdbcTemplate.query("""
                            SELECT column_name, data_type, is_nullable, column_default
                            FROM information_schema.columns
                            WHERE table_schema = ? AND table_name = ?
                            ORDER BY ordinal_position
                            """,
                    (rs, rowNum) -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("column", rs.getString("column_name"));
                        row.put("type", rs.getString("data_type"));
                        row.put("nullable", rs.getString("is_nullable"));
                        row.put("defaultValue", rs.getString("column_default"));
                        return row;
                    },
                    schema, normalizedTable
            );
            return Map.of(
                    "schema", schema,
                    "table", normalizedTable,
                    "columns", columns,
                    "count", columns.size()
            );
        } catch (IllegalArgumentException e) {
            return Map.of("error", e.getMessage());
        } catch (Exception e) {
            log.error("[Tool] describeTable failed: table={}, error={}", tableName, e.getMessage(), e);
            return Map.of("error", "查看表结构失败: " + e.getMessage());
        }
    }

    @Tool(description = "预览一条只读 SQL 的执行计划，不真正返回业务数据，适合在正式查询前检查扫描范围和索引使用情况。")
    public Map<String, Object> explainQuery(
            @ToolParam(description = "要预览执行计划的 SQL，只支持 SELECT 或 WITH 查询") String sql) {
        log.info("[Tool] explainQuery: sql={}", sql);
        try {
            String safeSql = validateAndNormalizeReadOnlySql(sql);
            List<String> plan = jdbcTemplate.execute((ConnectionCallback<List<String>>) connection -> {
                connection.setReadOnly(true);
                connection.setAutoCommit(false);
                try (var stmt = connection.createStatement()) {
                    stmt.setQueryTimeout(config.getQueryTimeoutSeconds());
                    try (var rs = stmt.executeQuery("EXPLAIN " + safeSql)) {
                        List<String> rows = new ArrayList<>();
                        while (rs.next()) {
                            rows.add(rs.getString(1));
                        }
                        return rows;
                    }
                } finally {
                    connection.rollback();
                    connection.setReadOnly(false);
                }
            });
            return Map.of(
                    "sql", safeSql,
                    "plan", plan,
                    "steps", plan.size()
            );
        } catch (IllegalArgumentException e) {
            return Map.of("error", e.getMessage());
        } catch (Exception e) {
            log.error("[Tool] explainQuery failed: sql={}, error={}", sql, e.getMessage(), e);
            return Map.of("error", "执行计划预览失败: " + e.getMessage());
        }
    }

    @Tool(description = "执行只读 SQL 查询，返回数据库查询结果。仅支持 SELECT 或 WITH 查询，会自动做安全检查和 LIMIT 限制。")
    public Map<String, Object> executeQuery(
            @ToolParam(description = "要执行的 SQL 查询语句，仅支持 SELECT 或 WITH") String sql) {
        log.info("[Tool] executeQuery: sql={}", sql);
        try {
            String finalSql = validateAndNormalizeReadOnlySql(sql);
            long startMs = System.currentTimeMillis();

            List<Map<String, Object>> rows = jdbcTemplate.execute(
                    (ConnectionCallback<List<Map<String, Object>>>) connection -> {
                        connection.setReadOnly(true);
                        connection.setAutoCommit(false);
                        try (var stmt = connection.createStatement()) {
                            stmt.setQueryTimeout(config.getQueryTimeoutSeconds());
                            try (var rs = stmt.executeQuery(finalSql)) {
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
                            }
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
            result.put("sql", finalSql);
            result.put("columns", columns);
            result.put("rows", rows != null ? rows : List.of());
            result.put("totalRows", rows != null ? rows.size() : 0);
            result.put("executionTime", elapsed + "ms");
            return result;
        } catch (IllegalArgumentException e) {
            return Map.of("error", e.getMessage());
        } catch (Exception e) {
            log.error("[Tool] executeQuery failed: sql={}, error={}", sql, e.getMessage(), e);
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
        result.put("suggestion", "请先使用 executeQuery 获取实际数据，然后基于返回数据生成前端可用的 ECharts 配置");
        result.put("templateUrl", "https://echarts.apache.org/examples");
        return result;
    }

    @Tool(description = "分析数据表的统计特征，包括记录数、平均值、最小值、最大值和标准差。")
    public Map<String, Object> analyzeDataset(
            @ToolParam(description = "数据库表名，仅支持白名单内的数据表") String datasetName,
            @ToolParam(description = "要分析的数值列名，逗号分隔") String metrics) {
        log.info("[Tool] analyzeDataset: dataset={}, metrics={}", datasetName, metrics);
        try {
            String tableName = normalizeSimpleIdentifier(datasetName, "表名");
            if (!isTableAllowed(tableName)) {
                return Map.of("error", "当前表不在只读助手允许范围内: " + tableName);
            }

            String[] columns = metrics.split(",");
            for (String col : columns) {
                normalizeSimpleIdentifier(col.trim(), "列名");
            }

            StringBuilder sqlBuilder = new StringBuilder("SELECT COUNT(*) AS record_count");
            for (String col : columns) {
                String c = col.trim();
                sqlBuilder.append(String.format(
                        ", AVG(%s) AS %s_avg, MIN(%s) AS %s_min, MAX(%s) AS %s_max, STDDEV(%s) AS %s_stddev",
                        c, c, c, c, c, c, c, c));
            }
            sqlBuilder.append(" FROM ").append(tableName);

            Map<String, Object> stats = jdbcTemplate.queryForMap(sqlBuilder.toString());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("dataset", tableName);
            result.put("recordCount", stats.get("record_count"));
            result.put("statistics", stats);
            return result;
        } catch (IllegalArgumentException e) {
            return Map.of("error", e.getMessage());
        } catch (Exception e) {
            log.error("[Tool] analyzeDataset failed: dataset={}, error={}", datasetName, e.getMessage(), e);
            return Map.of("error", "数据分析失败: " + e.getMessage());
        }
    }

    private String validateAndNormalizeReadOnlySql(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL 不能为空");
        }
        String normalized = sql.trim().replaceAll("\\s+", " ");
        String upper = normalized.toUpperCase(Locale.ROOT);

        if (!upper.startsWith("SELECT") && !upper.startsWith("WITH")) {
            throw new IllegalArgumentException("只允许 SELECT 查询语句或 WITH ... SELECT 查询");
        }
        if (normalized.contains(";")) {
            throw new IllegalArgumentException("不允许多条 SQL 语句");
        }
        if (normalized.contains("--") || normalized.contains("/*")) {
            throw new IllegalArgumentException("SQL 不允许包含注释");
        }
        for (String keyword : DANGEROUS_KEYWORDS) {
            if (upper.matches(".*\\b" + keyword + "\\b.*")) {
                throw new IllegalArgumentException("SQL 包含不允许的关键字: " + keyword);
            }
        }

        validateTableReferences(normalized);

        if (!upper.contains("LIMIT")) {
            normalized = normalized + " LIMIT " + config.getMaxRows();
        }
        return normalized;
    }

    private void validateTableReferences(String sql) {
        Set<String> referencedTables = new LinkedHashSet<>();
        Matcher matcher = TABLE_REFERENCE_PATTERN.matcher(sql);
        while (matcher.find()) {
            String rawTable = matcher.group(1);
            if (rawTable == null || rawTable.isBlank()) {
                continue;
            }
            String normalized = rawTable.contains(".")
                    ? rawTable.substring(rawTable.lastIndexOf('.') + 1)
                    : rawTable;
            referencedTables.add(normalized);
        }

        for (String table : referencedTables) {
            String safeTable = normalizeSimpleIdentifier(table, "表名");
            if (!isTableAllowed(safeTable)) {
                throw new IllegalArgumentException("当前表不在只读助手允许范围内: " + safeTable);
            }
        }
    }

    private boolean isTableAllowed(String tableName) {
        String normalized = tableName.toLowerCase(Locale.ROOT);
        List<String> blockedTables = config.getBlockedTables() != null ? config.getBlockedTables() : List.of();
        if (blockedTables.stream().map(t -> t.toLowerCase(Locale.ROOT)).anyMatch(normalized::equals)) {
            return false;
        }
        List<String> allowedTables = config.getAllowedTables();
        if (allowedTables == null || allowedTables.isEmpty()) {
            return true;
        }
        return allowedTables.stream().map(t -> t.toLowerCase(Locale.ROOT)).anyMatch(normalized::equals);
    }

    private String normalizeSimpleIdentifier(String identifier, String label) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        String normalized = identifier.trim();
        if (!normalized.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            throw new IllegalArgumentException("无效的" + label + ": " + identifier);
        }
        return normalized;
    }

    private String resolveSchema() {
        return (config.getDefaultSchema() == null || config.getDefaultSchema().isBlank())
                ? "public"
                : config.getDefaultSchema();
    }
}
