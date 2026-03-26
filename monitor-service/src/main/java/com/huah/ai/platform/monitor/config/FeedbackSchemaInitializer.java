package com.huah.ai.platform.monitor.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FeedbackSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void initialize() {
        try {
            jdbcTemplate.execute("""
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
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS ai_evidence_feedback (
                        id VARCHAR(36) PRIMARY KEY,
                        response_id VARCHAR(36) NOT NULL,
                        chunk_id VARCHAR(128) NOT NULL,
                        knowledge_base_id VARCHAR(64),
                        feedback VARCHAR(16) NOT NULL,
                        comment VARCHAR(255),
                        created_at TIMESTAMP DEFAULT NOW(),
                        updated_at TIMESTAMP DEFAULT NOW(),
                        CONSTRAINT uk_evidence_feedback UNIQUE (response_id, chunk_id)
                    )
                    """);
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_feedback_created ON ai_response_feedback (created_at DESC)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_feedback_source ON ai_response_feedback (source_type, feedback)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_evidence_feedback_created ON ai_evidence_feedback (created_at DESC)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_evidence_feedback_chunk ON ai_evidence_feedback (chunk_id)");
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS ai_alert_workflow (
                        fingerprint VARCHAR(255) PRIMARY KEY,
                        workflow_status VARCHAR(32) NOT NULL,
                        workflow_note VARCHAR(255),
                        silenced_until TIMESTAMP,
                        updated_at TIMESTAMP DEFAULT NOW()
                    )
                    """);
            jdbcTemplate.execute("ALTER TABLE ai_alert_workflow ADD COLUMN IF NOT EXISTS silenced_until TIMESTAMP");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_alert_workflow_status ON ai_alert_workflow (workflow_status, updated_at DESC)");
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS ai_alert_workflow_history (
                        id BIGSERIAL PRIMARY KEY,
                        fingerprint VARCHAR(255) NOT NULL,
                        workflow_status VARCHAR(32) NOT NULL,
                        workflow_note VARCHAR(255),
                        silenced_until TIMESTAMP,
                        created_at TIMESTAMP DEFAULT NOW()
                    )
                    """);
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_alert_workflow_history_fp ON ai_alert_workflow_history (fingerprint, created_at DESC)");
            jdbcTemplate.execute("ALTER TABLE ai_audit_logs ADD COLUMN IF NOT EXISTS phase_breakdown_json TEXT");
            jdbcTemplate.execute("""
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
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_tool_audit_created ON ai_tool_audit_logs (created_at DESC)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_tool_audit_trace ON ai_tool_audit_logs (trace_id, created_at DESC)");
        } catch (Exception e) {
            log.warn("initialize ai_response_feedback table failed: {}", e.getMessage());
        }
    }
}
