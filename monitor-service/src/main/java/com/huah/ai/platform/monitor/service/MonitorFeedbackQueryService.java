package com.huah.ai.platform.monitor.service;

import com.huah.ai.platform.monitor.model.EvidenceFeedbackSampleResponse;
import com.huah.ai.platform.monitor.model.FeedbackOverviewResponse;
import com.huah.ai.platform.monitor.model.FeedbackSampleResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorFeedbackQueryService {

    private final JdbcTemplate jdbcTemplate;

    public FeedbackOverviewResponse getFeedbackOverview() {
        try {
            String today = LocalDate.now().toString();
            var stats = jdbcTemplate.queryForMap(
                    "SELECT COUNT(*) AS total, " +
                            "COUNT(*) FILTER (WHERE feedback = 'up') AS positive, " +
                            "COUNT(*) FILTER (WHERE feedback = 'down') AS negative " +
                            "FROM ai_response_feedback WHERE created_at >= ?::date",
                    today
            );
            long total = ((Number) stats.get("total")).longValue();
            long positive = ((Number) stats.get("positive")).longValue();
            long negative = ((Number) stats.get("negative")).longValue();
            return FeedbackOverviewResponse.builder()
                    .totalCount(total)
                    .positiveCount(positive)
                    .negativeCount(negative)
                    .positiveRate(total > 0 ? (double) positive / total : 0)
                    .build();
        } catch (RuntimeException e) {
            log.warn("Failed to load feedback overview: {}", e.getMessage());
            return FeedbackOverviewResponse.builder()
                    .totalCount(0)
                    .positiveCount(0)
                    .negativeCount(0)
                    .positiveRate(0)
                    .build();
        }
    }

    public List<FeedbackSampleResponse> getRecentFeedback(int limit) {
        try {
            return jdbcTemplate.query(
                    "SELECT f.response_id, a.user_id, f.source_type, a.agent_type, f.knowledge_base_id, f.feedback, f.comment, f.created_at " +
                            "FROM ai_response_feedback f " +
                            "LEFT JOIN ai_audit_logs a ON a.id = f.response_id " +
                            "ORDER BY f.created_at DESC LIMIT ?",
                    (rs, rowNum) -> FeedbackSampleResponse.builder()
                            .responseId(rs.getString("response_id"))
                            .userId(rs.getString("user_id"))
                            .sourceType(rs.getString("source_type"))
                            .agentType(rs.getString("agent_type"))
                            .knowledgeBaseId(rs.getString("knowledge_base_id"))
                            .feedback(rs.getString("feedback"))
                            .comment(rs.getString("comment"))
                            .createdAt(toLocalDateTime(rs.getTimestamp("created_at")))
                            .build(),
                    limit
            );
        } catch (RuntimeException e) {
            log.warn("Failed to load recent feedback: limit={}, error={}", limit, e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<EvidenceFeedbackSampleResponse> getRecentEvidenceFeedback(int limit) {
        try {
            return jdbcTemplate.query(
                    "SELECT e.response_id, e.chunk_id, a.user_id, e.knowledge_base_id, e.feedback, e.comment, e.created_at " +
                            "FROM ai_evidence_feedback e " +
                            "LEFT JOIN ai_audit_logs a ON a.id = e.response_id " +
                            "ORDER BY e.created_at DESC LIMIT ?",
                    (rs, rowNum) -> EvidenceFeedbackSampleResponse.builder()
                            .responseId(rs.getString("response_id"))
                            .chunkId(rs.getString("chunk_id"))
                            .userId(rs.getString("user_id"))
                            .knowledgeBaseId(rs.getString("knowledge_base_id"))
                            .feedback(rs.getString("feedback"))
                            .comment(rs.getString("comment"))
                            .createdAt(toLocalDateTime(rs.getTimestamp("created_at")))
                            .build(),
                    limit
            );
        } catch (RuntimeException e) {
            log.warn("Failed to load recent evidence feedback: limit={}, error={}", limit, e.getMessage());
            return Collections.emptyList();
        }
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp != null ? timestamp.toLocalDateTime() : null;
    }
}

