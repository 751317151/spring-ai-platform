package com.huah.ai.platform.agent.tools;

import com.huah.ai.platform.agent.audit.ToolExecutionContext;
import com.huah.ai.platform.agent.config.ToolsProperties;
import com.huah.ai.platform.agent.support.AgentTestFixtures;
import com.huah.ai.platform.agent.security.ToolSecurityService;
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
        ToolsProperties properties = AgentTestFixtures.toolsProperties();
        properties.getDataAnalysis().setDefaultSchema("public");
        properties.getDataAnalysis().setAllowedTables(List.of("knowledge_bases", "document_meta"));
        properties.getDataAnalysis().setBlockedTables(List.of("ai_users"));
        properties.getSecurity().setEnabled(true);
        properties.getSecurity().getAgentDataScopeAllowlist().put("rd", List.of("public.knowledge_bases", "public.document_meta"));
        properties.getSecurity().getAgentDataSourceAllowlist().put("rd", List.of("knowledge"));
        properties.getSecurity().getSchemaDataSourceBindings().put("public", "knowledge");
        properties.getSecurity().getSchemaDataSourceBindings().put("analytics", "warehouse");
        dataAnalysisTools = new DataAnalysisTools(
                jdbcTemplate, properties, AgentTestFixtures.toolSecurityService(properties));
        ToolExecutionContext.set("u-1", "s-1", "rd");
    }

    @Test
    void shouldRejectNonSelectSql() {
        Map<String, Object> result = dataAnalysisTools.executeQuery("delete from knowledge_bases");
        assertTrue(String.valueOf(result.get("error")).contains("Only SELECT"));
    }

    @Test
    void shouldRejectBlockedTable() {
        Map<String, Object> result = dataAnalysisTools.executeQuery("select * from ai_users");
        assertTrue(String.valueOf(result.get("error")).contains("outside"));
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
        assertTrue(String.valueOf(result.get("error")).contains("outside the allowed scope"));
    }

    @Test
    void shouldRejectCrossSchemaQuery() {
        Map<String, Object> result = dataAnalysisTools.executeQuery(
                "select * from public.knowledge_bases kb join analytics.document_meta dm on kb.id = dm.id"
        );

        assertTrue(String.valueOf(result.get("error")).contains("Cross-schema queries are not allowed"));
        assertTrue(String.valueOf(result.get("error")).contains("analytics:public"));
    }

    @Test
    void shouldRejectSchemaOutsideScope() {
        Map<String, Object> result = dataAnalysisTools.executeQuery("select * from analytics.knowledge_bases");

        assertTrue(String.valueOf(result.get("error")).contains("outside the current agent boundary"));
        assertTrue(String.valueOf(result.get("error")).contains("dataSource=warehouse"));
    }
}
