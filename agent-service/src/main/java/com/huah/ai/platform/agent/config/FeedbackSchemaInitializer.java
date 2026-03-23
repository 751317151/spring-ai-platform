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
        } catch (Exception e) {
            log.warn("initialize ai_response_feedback table failed: {}", e.getMessage());
        }
    }
}
