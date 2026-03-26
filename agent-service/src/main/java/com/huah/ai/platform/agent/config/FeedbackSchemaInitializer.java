package com.huah.ai.platform.agent.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

@Slf4j
@Component
@RequiredArgsConstructor
public class FeedbackSchemaInitializer {

    private final DataSource dataSource;

    @PostConstruct
    public void initialize() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS ai_response_feedback (
                        id VARCHAR(36) PRIMARY KEY,
                        response_id VARCHAR(36) NOT NULL UNIQUE,
                        source_type VARCHAR(32) NOT NULL,
                        knowledge_base_id VARCHAR(64),
                        feedback VARCHAR(16) NOT NULL,
                        comment VARCHAR(255),
                        created_at TIMESTAMP DEFAULT NOW(),
                        updated_at TIMESTAMP DEFAULT NOW()
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_feedback_created ON ai_response_feedback (created_at DESC)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_feedback_source ON ai_response_feedback (source_type, feedback)");
            statement.execute("ALTER TABLE ai_audit_logs ADD COLUMN IF NOT EXISTS trace_id VARCHAR(64)");
            statement.execute("ALTER TABLE ai_audit_logs ADD COLUMN IF NOT EXISTS phase_breakdown_json TEXT");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_audit_trace_id ON ai_audit_logs (trace_id)");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS ai_tool_audit_logs (
                        id VARCHAR(36) PRIMARY KEY,
                        user_id VARCHAR(64),
                        session_id VARCHAR(128),
                        agent_type VARCHAR(64),
                        tool_name VARCHAR(128) NOT NULL,
                        tool_class VARCHAR(128),
                        input_summary TEXT,
                        output_summary TEXT,
                        success BOOLEAN NOT NULL,
                        error_message VARCHAR(500),
                        latency_ms BIGINT,
                        trace_id VARCHAR(64),
                        created_at TIMESTAMP DEFAULT NOW()
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_tool_audit_created ON ai_tool_audit_logs (created_at DESC)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_tool_audit_lookup ON ai_tool_audit_logs (user_id, agent_type, tool_name)");
        } catch (Exception e) {
            log.warn("initialize ai_response_feedback table failed: {}", e.getMessage());
        }
    }
}
