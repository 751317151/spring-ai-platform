package com.huah.ai.platform.rag.config;

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
                        id BIGINT PRIMARY KEY,
                        response_id BIGINT NOT NULL UNIQUE,
                        source_type VARCHAR(32) NOT NULL,
                        knowledge_base_id BIGINT,
                        feedback VARCHAR(16) NOT NULL,
                        comment VARCHAR(255),
                        created_at TIMESTAMP DEFAULT NOW(),
                        updated_at TIMESTAMP DEFAULT NOW()
                    )
                    """);
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS ai_evidence_feedback (
                        id BIGINT PRIMARY KEY,
                        response_id BIGINT NOT NULL,
                        chunk_id VARCHAR(128) NOT NULL,
                        knowledge_base_id BIGINT,
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
            jdbcTemplate.execute("ALTER TABLE ai_audit_logs ADD COLUMN IF NOT EXISTS client_ip VARCHAR(64)");
            jdbcTemplate.execute("ALTER TABLE ai_audit_logs ADD COLUMN IF NOT EXISTS country VARCHAR(64)");
            jdbcTemplate.execute("ALTER TABLE ai_audit_logs ADD COLUMN IF NOT EXISTS province VARCHAR(64)");
            jdbcTemplate.execute("ALTER TABLE ai_audit_logs ADD COLUMN IF NOT EXISTS city VARCHAR(64)");
            jdbcTemplate.execute("ALTER TABLE ai_audit_logs ADD COLUMN IF NOT EXISTS trace_id VARCHAR(64)");
            jdbcTemplate.execute("ALTER TABLE ai_audit_logs ADD COLUMN IF NOT EXISTS user_id VARCHAR(64)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_audit_trace_id ON ai_audit_logs (trace_id)");
            jdbcTemplate.execute("ALTER TABLE knowledge_bases ADD COLUMN IF NOT EXISTS visibility_scope VARCHAR(16) DEFAULT 'DEPARTMENT'");
            jdbcTemplate.execute("ALTER TABLE knowledge_bases ADD COLUMN IF NOT EXISTS chunk_strategy VARCHAR(16) DEFAULT 'TOKEN'");
            jdbcTemplate.execute("ALTER TABLE knowledge_bases ADD COLUMN IF NOT EXISTS structured_batch_size INT DEFAULT 20");
        } catch (Exception e) {
            log.warn("initialize ai_response_feedback table failed: {}", e.getMessage());
        }
    }
}
