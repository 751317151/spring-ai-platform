package com.huah.ai.platform.rag.service;

import com.huah.ai.platform.common.exception.BizException;
import com.huah.ai.platform.common.trace.TraceIdContext;
import com.huah.ai.platform.common.util.SnowflakeIdGenerator;
import com.huah.ai.platform.rag.model.RagEvaluationOverview;
import com.huah.ai.platform.rag.model.RagEvaluationSample;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class RagAuditService {

    private final JdbcTemplate jdbcTemplate;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    public Long saveQueryLog(String userId,
                               Long knowledgeBaseId,
                               String question,
                               String answer,
                               long latencyMs,
                               boolean success,
                               String errorMessage) {
        Long responseId = snowflakeIdGenerator.nextLongId();
        jdbcTemplate.update(
                "INSERT INTO ai_audit_logs (id, user_id, agent_type, user_message, ai_response, latency_ms, success, error_message, session_id, trace_id, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                responseId,
                userId,
                "rag",
                truncate(question, 500),
                truncate(answer, 500),
                latencyMs,
                success,
                errorMessage,
                knowledgeBaseId,
                TraceIdContext.currentTraceId(),
                Timestamp.valueOf(LocalDateTime.now())
        );
        return responseId;
    }

    public void submitFeedback(String userId,
                               Long responseId,
                               Long knowledgeBaseId,
                               String feedback,
                               String comment) {
        if (responseId == null) {
            throw new BizException("responseId不能为空");
        }

        String owner = jdbcTemplate.query(
                "SELECT user_id FROM ai_audit_logs WHERE id = ?",
                rs -> rs.next() ? rs.getString(1) : null,
                responseId
        );
        if (owner == null) {
            throw new BizException("未找到对应回答记录");
        }
        if (owner != null && !owner.equals(userId)) {
            throw new BizException("无权提交该回答反馈");
        }

        String normalizedFeedback = normalizeFeedback(feedback);
        Integer updated = jdbcTemplate.update(
                "UPDATE ai_response_feedback SET feedback = ?, comment = ?, updated_at = ?, knowledge_base_id = ? WHERE response_id = ?",
                normalizedFeedback,
                trimComment(comment),
                Timestamp.valueOf(LocalDateTime.now()),
                knowledgeBaseId,
                responseId
        );
        if (updated != null && updated > 0) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                "INSERT INTO ai_response_feedback (id, response_id, source_type, knowledge_base_id, feedback, comment, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                snowflakeIdGenerator.nextLongId(),
                responseId,
                "rag",
                knowledgeBaseId,
                normalizedFeedback,
                trimComment(comment),
                Timestamp.valueOf(now),
                Timestamp.valueOf(now)
        );
    }

    public void submitEvidenceFeedback(String userId,
                                       Long responseId,
                                       String chunkId,
                                       Long knowledgeBaseId,
                                       String feedback,
                                       String comment) {
        if (responseId == null) {
            throw new BizException("responseId不能为空");
        }
        if (chunkId == null || chunkId.isBlank()) {
            throw new BizException("chunkId不能为空");
        }

        String owner = jdbcTemplate.query(
                "SELECT user_id FROM ai_audit_logs WHERE id = ?",
                rs -> rs.next() ? rs.getString(1) : null,
                responseId
        );
        if (owner == null) {
            throw new BizException("未找到对应回答记录");
        }
        if (!owner.equals(userId)) {
            throw new BizException("无权提交该证据反馈");
        }

        String normalizedFeedback = normalizeFeedback(feedback);
        int updated = jdbcTemplate.update(
                "UPDATE ai_evidence_feedback SET feedback = ?, comment = ?, knowledge_base_id = ?, updated_at = ? WHERE response_id = ? AND chunk_id = ?",
                normalizedFeedback,
                trimComment(comment),
                knowledgeBaseId,
                Timestamp.valueOf(LocalDateTime.now()),
                responseId,
                chunkId
        );
        if (updated > 0) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                "INSERT INTO ai_evidence_feedback (id, response_id, chunk_id, knowledge_base_id, feedback, comment, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                snowflakeIdGenerator.nextLongId(),
                responseId,
                chunkId,
                knowledgeBaseId,
                normalizedFeedback,
                trimComment(comment),
                Timestamp.valueOf(now),
                Timestamp.valueOf(now)
        );
    }

    public RagEvaluationOverview getEvaluationOverview(Long knowledgeBaseId) {
        try {
            String querySql = knowledgeBaseId == null
                    ? "SELECT COUNT(*) FROM ai_audit_logs WHERE agent_type = 'rag'"
                    : "SELECT COUNT(*) FROM ai_audit_logs WHERE agent_type = 'rag' AND session_id = ?";
            long totalQueries = (knowledgeBaseId == null)
                    ? jdbcTemplate.queryForObject(querySql, Long.class)
                    : jdbcTemplate.queryForObject(querySql, Long.class, String.valueOf(knowledgeBaseId));

            String feedbackSql = """
                    SELECT
                        COUNT(*) AS total,
                        COUNT(*) FILTER (WHERE feedback = 'up') AS positive,
                        COUNT(*) FILTER (WHERE feedback = 'down') AS negative
                    FROM ai_response_feedback
                    WHERE source_type = 'rag'
                    """ + (knowledgeBaseId == null ? "" : " AND knowledge_base_id = ?");
            var feedbackStats = (knowledgeBaseId == null)
                    ? jdbcTemplate.queryForMap(feedbackSql)
                    : jdbcTemplate.queryForMap(feedbackSql, knowledgeBaseId);

            String evidenceSql = """
                    SELECT
                        COUNT(*) AS total,
                        COUNT(*) FILTER (WHERE feedback = 'up') AS positive,
                        COUNT(*) FILTER (WHERE feedback = 'down') AS negative
                    FROM ai_evidence_feedback
                    """ + (knowledgeBaseId == null ? "" : " WHERE knowledge_base_id = ?");
            var evidenceStats = (knowledgeBaseId == null)
                    ? jdbcTemplate.queryForMap(evidenceSql)
                    : jdbcTemplate.queryForMap(evidenceSql, knowledgeBaseId);

            long feedbackCount = getLong(feedbackStats, "total");
            long positiveFeedback = getLong(feedbackStats, "positive");
            long negativeFeedback = getLong(feedbackStats, "negative");
            long evidenceCount = getLong(evidenceStats, "total");
            long positiveEvidence = getLong(evidenceStats, "positive");
            long negativeEvidence = getLong(evidenceStats, "negative");

            return RagEvaluationOverview.builder()
                    .totalQueries(totalQueries)
                    .feedbackCount(feedbackCount)
                    .positiveFeedbackCount(positiveFeedback)
                    .negativeFeedbackCount(negativeFeedback)
                    .positiveFeedbackRate(feedbackCount > 0 ? (double) positiveFeedback / feedbackCount : 0d)
                    .evidenceFeedbackCount(evidenceCount)
                    .positiveEvidenceCount(positiveEvidence)
                    .negativeEvidenceCount(negativeEvidence)
                    .positiveEvidenceRate(evidenceCount > 0 ? (double) positiveEvidence / evidenceCount : 0d)
                    .lowRatedQueryCount(negativeFeedback + negativeEvidence)
                    .build();
        } catch (Exception e) {
            return RagEvaluationOverview.builder().build();
        }
    }

    public List<RagEvaluationSample> getLowRatedSamples(Long knowledgeBaseId, int limit) {
        try {
            String sql = """
                    SELECT
                        a.id AS response_id,
                        a.user_id,
                        COALESCE(r.knowledge_base_id, e.knowledge_base_id, a.session_id) AS knowledge_base_id,
                        a.user_message,
                        a.ai_response,
                        COALESCE(r.feedback, 'down') AS feedback,
                        COALESCE(r.comment, '') AS comment,
                        COALESCE(e.negative_evidence_count, 0) AS evidence_negative_count,
                        a.created_at
                    FROM ai_audit_logs a
                    LEFT JOIN ai_response_feedback r ON r.response_id = a.id AND r.source_type = 'rag'
                    LEFT JOIN (
                        SELECT response_id, knowledge_base_id,
                               COUNT(*) FILTER (WHERE feedback = 'down') AS negative_evidence_count
                        FROM ai_evidence_feedback
                        GROUP BY response_id, knowledge_base_id
                    ) e ON e.response_id = a.id
                    WHERE a.agent_type = 'rag'
                      AND (COALESCE(r.feedback, '') = 'down' OR COALESCE(e.negative_evidence_count, 0) > 0)
                    """ + (knowledgeBaseId == null ? "" : " AND COALESCE(r.knowledge_base_id, e.knowledge_base_id, a.session_id) = ?") +
                    " ORDER BY a.created_at DESC LIMIT ?";
            Object[] args = (knowledgeBaseId == null)
                    ? new Object[]{limit}
                    : new Object[]{knowledgeBaseId, limit};
            return jdbcTemplate.query(sql,
                    (rs, rowNum) -> RagEvaluationSample.builder()
                            .responseId(rs.getLong("response_id"))
                            .userId(rs.getString("user_id"))
                            .knowledgeBaseId(rs.getLong("knowledge_base_id"))
                            .question(rs.getString("user_message"))
                            .answer(rs.getString("ai_response"))
                            .feedback(rs.getString("feedback"))
                            .comment(rs.getString("comment"))
                            .evidenceNegativeCount(rs.getLong("evidence_negative_count"))
                            .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                            .build(),
                    args);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private String normalizeFeedback(String feedback) {
        String normalized = feedback == null ? "" : feedback.trim().toLowerCase(Locale.ROOT);
        if (!"up".equals(normalized) && !"down".equals(normalized)) {
            throw new BizException("feedback仅支持up或down");
        }
        return normalized;
    }

    private String trimComment(String comment) {
        if (comment == null) {
            return null;
        }
        String normalized = comment.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized.length() > 255 ? normalized.substring(0, 255) : normalized;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    private long getLong(java.util.Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof Number number ? number.longValue() : 0L;
    }
}
