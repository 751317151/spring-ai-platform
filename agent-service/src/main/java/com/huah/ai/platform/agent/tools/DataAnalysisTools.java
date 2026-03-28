package com.huah.ai.platform.agent.tools;

import com.huah.ai.platform.agent.audit.ToolExecutionContext;
import com.huah.ai.platform.agent.config.ToolsProperties;
import com.huah.ai.platform.agent.security.ToolAccessDecision;
import com.huah.ai.platform.agent.security.ToolSecurityService;
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
    private final ToolSecurityService toolSecurityService;

    public DataAnalysisTools(JdbcTemplate jdbcTemplate,
                             ToolsProperties props,
                             ToolSecurityService toolSecurityService) {
        this.jdbcTemplate = jdbcTemplate;
        this.config = props.getDataAnalysis();
        this.toolSecurityService = toolSecurityService;
    }

    @Tool(description = "List tables or views visible to the current read-only SQL agent.")
    public Map<String, Object> listAccessibleTables() {
        log.info("[Tool] listAccessibleTables");
        try {
            String schema = resolveSchema();
            ToolAccessDecision decision = toolSecurityService.decideDataScopeAccess(currentAgentType(), schema, List.of());
            if (!decision.isAllowed()) {
                return ToolResponseSupport.error(decision.getReasonMessage(), "DATA_SCOPE_DENIED");
            }
            List<Map<String, Object>> tables = jdbcTemplate.execute((ConnectionCallback<List<Map<String, Object>>>) connection -> {
                DatabaseMetaData metaData = connection.getMetaData();
                List<Map<String, Object>> rows = new ArrayList<>();
                try (var rs = metaData.getTables(null, schema, "%", new String[]{"TABLE", "VIEW"})) {
                    while (rs.next()) {
                        String tableName = rs.getString("TABLE_NAME");
                        if (!isTableAllowed(tableName)) {
                            continue;
                        }
                        ToolAccessDecision tableDecision = toolSecurityService.decideDataScopeAccess(
                                currentAgentType(),
                                schema,
                                List.of(tableName)
                        );
                        if (!tableDecision.isAllowed()) {
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
            return ToolResponseSupport.error("List tables failed: " + e.getMessage(), "LIST_TABLES_FAILED");
        }
    }

    @Tool(description = "Describe columns for a single allowed table.")
    public Map<String, Object> describeTable(
            @ToolParam(description = "Table name in the current allowed scope.") String tableName) {
        log.info("[Tool] describeTable: table={}", tableName);
        try {
            String normalizedTable = normalizeSimpleIdentifier(tableName, "table");
            if (!isTableAllowed(normalizedTable)) {
                return ToolResponseSupport.error("Table is outside the allowed scope: " + normalizedTable, "TABLE_SCOPE_DENIED");
            }

            String schema = resolveSchema();
            ToolAccessDecision decision = toolSecurityService.decideDataScopeAccess(currentAgentType(), schema, List.of(normalizedTable));
            if (!decision.isAllowed()) {
                return ToolResponseSupport.error(decision.getReasonMessage(), "DATA_SCOPE_DENIED");
            }

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
            return ToolResponseSupport.error(e.getMessage(), "INVALID_ARGUMENT");
        } catch (Exception e) {
            log.error("[Tool] describeTable failed: table={}, error={}", tableName, e.getMessage(), e);
            return ToolResponseSupport.error("Describe table failed: " + e.getMessage(), "DESCRIBE_TABLE_FAILED");
        }
    }

    @Tool(description = "Preview the execution plan for a read-only SQL query.")
    public Map<String, Object> explainQuery(
            @ToolParam(description = "SQL that must be a SELECT or WITH query.") String sql) {
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
            return ToolResponseSupport.error(e.getMessage(), "INVALID_ARGUMENT");
        } catch (Exception e) {
            log.error("[Tool] explainQuery failed: sql={}, error={}", sql, e.getMessage(), e);
            return ToolResponseSupport.error("Explain query failed: " + e.getMessage(), "EXPLAIN_QUERY_FAILED");
        }
    }

    @Tool(description = "Execute a read-only SQL query. Only SELECT or WITH queries are allowed.")
    public Map<String, Object> executeQuery(
            @ToolParam(description = "SQL query. Only SELECT or WITH is allowed.") String sql) {
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
            return ToolResponseSupport.error(e.getMessage(), "INVALID_ARGUMENT");
        } catch (Exception e) {
            log.error("[Tool] executeQuery failed: sql={}, error={}", sql, e.getMessage(), e);
            return ToolResponseSupport.error("SQL execution failed: " + e.getMessage(), "SQL_EXECUTION_FAILED");
        }
    }

    @Tool(description = "Generate chart metadata after a query result is prepared.")
    public Map<String, Object> generateChart(
            @ToolParam(description = "Chart type: bar|line|pie|scatter") String chartType,
            @ToolParam(description = "Data description") String data,
            @ToolParam(description = "Chart title") String title) {
        log.info("[Tool] generateChart: type={}, title={}", chartType, title);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("chartType", chartType);
        result.put("title", title);
        result.put("dataDescription", data);
        result.put("suggestion", "Run executeQuery first, then build chart options on top of real rows");
        result.put("templateUrl", "https://echarts.apache.org/examples");
        return result;
    }

    @Tool(description = "Compute summary statistics for allowed numeric columns in a table.")
    public Map<String, Object> analyzeDataset(
            @ToolParam(description = "Dataset table name in the allowed scope") String datasetName,
            @ToolParam(description = "Numeric columns separated by comma") String metrics) {
        log.info("[Tool] analyzeDataset: dataset={}, metrics={}", datasetName, metrics);
        try {
            String tableName = normalizeSimpleIdentifier(datasetName, "table");
            if (!isTableAllowed(tableName)) {
                return ToolResponseSupport.error("Table is outside the allowed scope: " + tableName, "TABLE_SCOPE_DENIED");
            }
            ToolAccessDecision decision = toolSecurityService.decideDataScopeAccess(currentAgentType(), resolveSchema(), List.of(tableName));
            if (!decision.isAllowed()) {
                return ToolResponseSupport.error(decision.getReasonMessage(), "DATA_SCOPE_DENIED");
            }

            String[] columns = metrics.split(",");
            for (String col : columns) {
                normalizeSimpleIdentifier(col.trim(), "column");
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
            return ToolResponseSupport.error(e.getMessage(), "INVALID_ARGUMENT");
        } catch (Exception e) {
            log.error("[Tool] analyzeDataset failed: dataset={}, error={}", datasetName, e.getMessage(), e);
            return ToolResponseSupport.error("Analyze dataset failed: " + e.getMessage(), "ANALYZE_DATASET_FAILED");
        }
    }

    private String validateAndNormalizeReadOnlySql(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL must not be empty");
        }
        String normalized = sql.trim().replaceAll("\\s+", " ");
        String upper = normalized.toUpperCase(Locale.ROOT);

        if (!upper.startsWith("SELECT") && !upper.startsWith("WITH")) {
            throw new IllegalArgumentException("Only SELECT or WITH queries are allowed");
        }
        if (normalized.contains(";")) {
            throw new IllegalArgumentException("Multiple SQL statements are not allowed");
        }
        if (normalized.contains("--") || normalized.contains("/*")) {
            throw new IllegalArgumentException("SQL comments are not allowed");
        }
        for (String keyword : DANGEROUS_KEYWORDS) {
            if (upper.matches(".*\\b" + keyword + "\\b.*")) {
                throw new IllegalArgumentException("SQL contains a forbidden keyword: " + keyword);
            }
        }

        validateTableReferences(normalized);

        if (!upper.contains("LIMIT")) {
            normalized = normalized + " LIMIT " + config.getMaxRows();
        }
        return normalized;
    }

    private void validateTableReferences(String sql) {
        Set<TableRef> referencedTables = new LinkedHashSet<>();
        Matcher matcher = TABLE_REFERENCE_PATTERN.matcher(sql);
        while (matcher.find()) {
            String rawTable = matcher.group(1);
            if (rawTable == null || rawTable.isBlank()) {
                continue;
            }
            referencedTables.add(parseTableRef(rawTable));
        }

        List<ToolSecurityService.DataScopeTarget> targets = referencedTables.isEmpty()
                ? List.of(new ToolSecurityService.DataScopeTarget(resolveSchema(), null))
                : referencedTables.stream()
                .map(item -> new ToolSecurityService.DataScopeTarget(item.schema(), item.table()))
                .toList();

        ToolAccessDecision decision = toolSecurityService.decideDataScopeAccess(currentAgentType(), targets);
        if (!decision.isAllowed()) {
            String detail = decision.getDetail();
            throw new IllegalArgumentException(
                    detail == null || detail.isBlank()
                            ? decision.getReasonMessage()
                            : decision.getReasonMessage() + " [" + detail + "]"
            );
        }

        for (TableRef tableRef : referencedTables) {
            if (!isTableAllowed(tableRef.table())) {
                throw new IllegalArgumentException("Table is outside the allowed scope: " + tableRef.table());
            }
        }
    }

    private TableRef parseTableRef(String rawTable) {
        String normalized = rawTable.trim();
        String[] parts = normalized.split("\\.");
        if (parts.length == 1) {
            return new TableRef(resolveSchema(), normalizeSimpleIdentifier(parts[0], "table"));
        }
        if (parts.length == 2) {
            return new TableRef(
                    normalizeSimpleIdentifier(parts[0], "schema"),
                    normalizeSimpleIdentifier(parts[1], "table")
            );
        }
        throw new IllegalArgumentException("Unsupported qualified table reference: " + rawTable);
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
            throw new IllegalArgumentException(label + " must not be empty");
        }
        String normalized = identifier.trim();
        if (!normalized.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            throw new IllegalArgumentException("Invalid " + label + ": " + identifier);
        }
        return normalized;
    }

    private String resolveSchema() {
        return (config.getDefaultSchema() == null || config.getDefaultSchema().isBlank())
                ? "public"
                : config.getDefaultSchema();
    }

    private String currentAgentType() {
        ToolExecutionContext.Context context = ToolExecutionContext.current();
        return context != null ? context.getAgentType() : null;
    }

    private record TableRef(String schema, String table) {
    }
}
