package com.huah.ai.platform.agent.tools;

import com.huah.ai.platform.agent.config.ToolsProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataAnalysisToolsTest {

    private JdbcTemplate jdbcTemplate;
    private DataAnalysisTools dataAnalysisTools;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        ToolsProperties properties = new ToolsProperties();
        properties.getDataAnalysis().setDefaultSchema("public");
        properties.getDataAnalysis().setAllowedTables(List.of("knowledge_bases", "document_meta"));
        properties.getDataAnalysis().setBlockedTables(List.of("ai_users"));
        dataAnalysisTools = new DataAnalysisTools(jdbcTemplate, properties);
    }

    @Test
    void shouldRejectNonSelectSql() {
        Map<String, Object> result = dataAnalysisTools.executeQuery("delete from knowledge_bases");
        assertTrue(String.valueOf(result.get("error")).contains("只允许 SELECT"));
    }

    @Test
    void shouldRejectBlockedTable() {
        Map<String, Object> result = dataAnalysisTools.executeQuery("select * from ai_users");
        assertTrue(String.valueOf(result.get("error")).contains("当前表不在只读助手允许范围内"));
    }

    @Test
    void shouldAppendLimitWhenMissing() {
        when(jdbcTemplate.execute(any(org.springframework.jdbc.core.ConnectionCallback.class))).thenReturn(List.of());

        Map<String, Object> result = dataAnalysisTools.executeQuery("select * from knowledge_bases");

        assertEquals("select * from knowledge_bases LIMIT 1000", result.get("sql"));
    }

    @Test
    void shouldDescribeAllowedTable() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("column", "id");
        row.put("type", "varchar");
        row.put("nullable", "NO");
        row.put("defaultValue", null);

        when(jdbcTemplate.query(any(String.class), any(org.springframework.jdbc.core.RowMapper.class), eq("public"), eq("knowledge_bases")))
                .thenReturn(List.of(row));

        Map<String, Object> result = dataAnalysisTools.describeTable("knowledge_bases");

        assertEquals("knowledge_bases", result.get("table"));
        verify(jdbcTemplate).query(any(String.class), any(org.springframework.jdbc.core.RowMapper.class), eq("public"), eq("knowledge_bases"));
    }

    @Test
    void shouldRejectAnalyzeForUnknownTable() {
        Map<String, Object> result = dataAnalysisTools.analyzeDataset("unknown_table", "amount");
        assertTrue(String.valueOf(result.get("error")).contains("当前表不在只读助手允许范围内"));
    }
}
