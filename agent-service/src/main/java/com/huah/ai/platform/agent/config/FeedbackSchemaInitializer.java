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
            statement.execute("CREATE INDEX IF NOT EXISTS idx_feedback_created ON ai_response_feedback (created_at DESC)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_feedback_source ON ai_response_feedback (source_type, feedback)");
            statement.execute("ALTER TABLE ai_audit_logs ADD COLUMN IF NOT EXISTS trace_id VARCHAR(64)");
            statement.execute("ALTER TABLE ai_audit_logs ADD COLUMN IF NOT EXISTS phase_breakdown_json TEXT");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_audit_trace_id ON ai_audit_logs (trace_id)");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS ai_tool_audit_logs (
                        id BIGINT PRIMARY KEY,
                        user_id VARCHAR(64),
                        session_id VARCHAR(128),
                        agent_type VARCHAR(64),
                        tool_name VARCHAR(128) NOT NULL,
                        tool_class VARCHAR(128),
                        input_summary TEXT,
                        output_summary TEXT,
                        success BOOLEAN NOT NULL,
                        error_message VARCHAR(500),
                        reason_code VARCHAR(128),
                        denied_resource VARCHAR(255),
                        latency_ms BIGINT,
                        trace_id VARCHAR(64),
                        created_at TIMESTAMP DEFAULT NOW()
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_tool_audit_created ON ai_tool_audit_logs (created_at DESC)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_tool_audit_lookup ON ai_tool_audit_logs (user_id, agent_type, tool_name)");
            statement.execute("ALTER TABLE ai_tool_audit_logs ADD COLUMN IF NOT EXISTS reason_code VARCHAR(128)");
            statement.execute("ALTER TABLE ai_tool_audit_logs ADD COLUMN IF NOT EXISTS denied_resource VARCHAR(255)");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS ai_multi_agent_traces (
                        id BIGINT PRIMARY KEY,
                        trace_id VARCHAR(64) NOT NULL UNIQUE,
                        user_id VARCHAR(64) NOT NULL,
                        session_id VARCHAR(128) NOT NULL,
                        agent_type VARCHAR(64) NOT NULL,
                        request_summary TEXT,
                        final_summary TEXT,
                        status VARCHAR(32) NOT NULL,
                        total_prompt_tokens INTEGER DEFAULT 0,
                        total_completion_tokens INTEGER DEFAULT 0,
                        total_latency_ms BIGINT DEFAULT 0,
                        step_count INTEGER DEFAULT 0,
                        error_message VARCHAR(500),
                        parent_trace_id VARCHAR(64),
                        recovery_source_trace_id VARCHAR(64),
                        recovery_source_step_order INTEGER,
                        recovery_action VARCHAR(32),
                        created_at TIMESTAMP DEFAULT NOW(),
                        updated_at TIMESTAMP DEFAULT NOW()
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_multi_trace_user_session ON ai_multi_agent_traces (user_id, session_id, created_at DESC)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_multi_trace_status ON ai_multi_agent_traces (status, created_at DESC)");
            statement.execute("ALTER TABLE ai_multi_agent_traces ADD COLUMN IF NOT EXISTS parent_trace_id VARCHAR(64)");
            statement.execute("ALTER TABLE ai_multi_agent_traces ADD COLUMN IF NOT EXISTS recovery_source_trace_id VARCHAR(64)");
            statement.execute("ALTER TABLE ai_multi_agent_traces ADD COLUMN IF NOT EXISTS recovery_source_step_order INTEGER");
            statement.execute("ALTER TABLE ai_multi_agent_traces ADD COLUMN IF NOT EXISTS recovery_action VARCHAR(32)");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS ai_multi_agent_trace_steps (
                        id BIGINT PRIMARY KEY,
                        trace_id VARCHAR(64) NOT NULL,
                        step_order INTEGER NOT NULL,
                        stage VARCHAR(32) NOT NULL,
                        agent_name VARCHAR(64) NOT NULL,
                        input_summary TEXT,
                        output_summary TEXT,
                        prompt_tokens INTEGER DEFAULT 0,
                        completion_tokens INTEGER DEFAULT 0,
                        latency_ms BIGINT DEFAULT 0,
                        success BOOLEAN NOT NULL,
                        error_message VARCHAR(500),
                        recoverable BOOLEAN DEFAULT TRUE,
                        skipped BOOLEAN DEFAULT FALSE,
                        recovery_action VARCHAR(32),
                        source_trace_id VARCHAR(64),
                        source_step_order INTEGER,
                        created_at TIMESTAMP DEFAULT NOW()
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_multi_trace_steps_lookup ON ai_multi_agent_trace_steps (trace_id, step_order)");
            statement.execute("ALTER TABLE ai_multi_agent_trace_steps ADD COLUMN IF NOT EXISTS recoverable BOOLEAN DEFAULT TRUE");
            statement.execute("ALTER TABLE ai_multi_agent_trace_steps ADD COLUMN IF NOT EXISTS skipped BOOLEAN DEFAULT FALSE");
            statement.execute("ALTER TABLE ai_multi_agent_trace_steps ADD COLUMN IF NOT EXISTS recovery_action VARCHAR(32)");
            statement.execute("ALTER TABLE ai_multi_agent_trace_steps ADD COLUMN IF NOT EXISTS source_trace_id VARCHAR(64)");
            statement.execute("ALTER TABLE ai_multi_agent_trace_steps ADD COLUMN IF NOT EXISTS source_step_order INTEGER");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS learning_favorites (
                        id BIGINT PRIMARY KEY,
                        user_id VARCHAR(64) NOT NULL,
                        response_id BIGINT,
                        role VARCHAR(32) NOT NULL,
                        content TEXT NOT NULL,
                        agent_type VARCHAR(64),
                        session_id VARCHAR(128),
                        session_summary VARCHAR(255),
                        source_message_index INTEGER,
                        created_at TIMESTAMP DEFAULT NOW(),
                        updated_at TIMESTAMP DEFAULT NOW(),
                        last_collected_at TIMESTAMP,
                        duplicate_count INTEGER DEFAULT 1,
                        tags_json TEXT,
                        session_config_snapshot_json TEXT
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_learning_favorites_user_time ON learning_favorites (user_id, last_collected_at DESC, created_at DESC)");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS learning_notes (
                        id BIGINT PRIMARY KEY,
                        user_id VARCHAR(64) NOT NULL,
                        title VARCHAR(255) NOT NULL,
                        content TEXT NOT NULL,
                        source_type VARCHAR(32),
                        related_favorite_id BIGINT,
                        related_session_id VARCHAR(128),
                        related_agent_type VARCHAR(64),
                        related_session_summary VARCHAR(255),
                        related_message_index INTEGER,
                        tags_json TEXT,
                        created_at TIMESTAMP DEFAULT NOW(),
                        updated_at TIMESTAMP DEFAULT NOW()
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_learning_notes_user_time ON learning_notes (user_id, updated_at DESC)");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS learning_followup_templates (
                        id BIGINT PRIMARY KEY,
                        user_id VARCHAR(64) NOT NULL,
                        name VARCHAR(255) NOT NULL,
                        content TEXT NOT NULL,
                        source_count INTEGER DEFAULT 0,
                        created_at TIMESTAMP DEFAULT NOW(),
                        updated_at TIMESTAMP DEFAULT NOW()
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_learning_templates_user_time ON learning_followup_templates (user_id, updated_at DESC)");
            statement.execute("ALTER TABLE learning_favorites ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT NOW()");
            statement.execute("ALTER TABLE learning_followup_templates ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT NOW()");
            statement.execute("ALTER TABLE learning_followup_templates ALTER COLUMN user_id TYPE VARCHAR(64)");
            statement.execute("""
                    DO $$
                    BEGIN
                        IF EXISTS (
                            SELECT 1
                            FROM information_schema.columns
                            WHERE table_name = 'learning_favorites'
                              AND column_name = 'created_at'
                              AND data_type IN ('bigint', 'integer')
                        ) THEN
                            EXECUTE 'ALTER TABLE learning_favorites ALTER COLUMN created_at TYPE TIMESTAMP USING to_timestamp(created_at / 1000.0)';
                        END IF;
                        IF EXISTS (
                            SELECT 1
                            FROM information_schema.columns
                            WHERE table_name = 'learning_favorites'
                              AND column_name = 'updated_at'
                              AND data_type IN ('bigint', 'integer')
                        ) THEN
                            EXECUTE 'ALTER TABLE learning_favorites ALTER COLUMN updated_at TYPE TIMESTAMP USING to_timestamp(updated_at / 1000.0)';
                        END IF;
                        IF EXISTS (
                            SELECT 1
                            FROM information_schema.columns
                            WHERE table_name = 'learning_favorites'
                              AND column_name = 'last_collected_at'
                              AND data_type IN ('bigint', 'integer')
                        ) THEN
                            EXECUTE 'ALTER TABLE learning_favorites ALTER COLUMN last_collected_at TYPE TIMESTAMP USING CASE WHEN last_collected_at IS NULL THEN NULL ELSE to_timestamp(last_collected_at / 1000.0) END';
                        END IF;
                    END $$;
                    """);
            statement.execute("""
                    DO $$
                    BEGIN
                        IF EXISTS (
                            SELECT 1
                            FROM information_schema.columns
                            WHERE table_name = 'learning_notes'
                              AND column_name = 'created_at'
                              AND data_type IN ('bigint', 'integer')
                        ) THEN
                            EXECUTE 'ALTER TABLE learning_notes ALTER COLUMN created_at TYPE TIMESTAMP USING to_timestamp(created_at / 1000.0)';
                        END IF;
                        IF EXISTS (
                            SELECT 1
                            FROM information_schema.columns
                            WHERE table_name = 'learning_notes'
                              AND column_name = 'updated_at'
                              AND data_type IN ('bigint', 'integer')
                        ) THEN
                            EXECUTE 'ALTER TABLE learning_notes ALTER COLUMN updated_at TYPE TIMESTAMP USING to_timestamp(updated_at / 1000.0)';
                        END IF;
                    END $$;
                    """);
            statement.execute("""
                    DO $$
                    BEGIN
                        IF EXISTS (
                            SELECT 1
                            FROM information_schema.columns
                            WHERE table_name = 'learning_followup_templates'
                              AND column_name = 'created_at'
                              AND data_type IN ('bigint', 'integer')
                        ) THEN
                            EXECUTE 'ALTER TABLE learning_followup_templates ALTER COLUMN created_at TYPE TIMESTAMP USING to_timestamp(created_at / 1000.0)';
                        END IF;
                        IF EXISTS (
                            SELECT 1
                            FROM information_schema.columns
                            WHERE table_name = 'learning_followup_templates'
                              AND column_name = 'updated_at'
                              AND data_type IN ('bigint', 'integer')
                        ) THEN
                            EXECUTE 'ALTER TABLE learning_followup_templates ALTER COLUMN updated_at TYPE TIMESTAMP USING to_timestamp(updated_at / 1000.0)';
                        END IF;
                    END $$;
                    """);
        } catch (Exception e) {
            log.warn("initialize ai_response_feedback table failed: {}", e.getMessage());
        }
    }
}
