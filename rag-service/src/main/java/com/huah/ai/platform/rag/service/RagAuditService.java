package com.huah.ai.platform.rag.service;

import com.huah.ai.platform.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RagAuditService {

    private final JdbcTemplate jdbcTemplate;

    public String saveQueryLog(String userId,
                               String knowledgeBaseId,
                               String question,
                               String answer,
                               long latencyMs,
                               boolean success,
                               String errorMessage) {
        String responseId = UUID.randomUUID().toString();
        jdbcTemplate.update(
                "INSERT INTO ai_audit_logs (id, user_id, agent_type, user_message, ai_response, latency_ms, success, error_message, session_id, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                responseId,
                userId,
                "rag",
                truncate(question, 500),
                truncate(answer, 500),
                latencyMs,
                success,
                errorMessage,
                knowledgeBaseId,
                Timestamp.valueOf(LocalDateTime.now())
        );
        return responseId;
    }

    public void submitFeedback(String userId,
                               String responseId,
                               String knowledgeBaseId,
                               String feedback,
                               String comment) {
        if (responseId == null || responseId.isBlank()) {
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
                UUID.randomUUID().toString(),
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
                                       String responseId,
                                       String chunkId,
                                       String knowledgeBaseId,
                                       String feedback,
                                       String comment) {
        if (responseId == null || responseId.isBlank()) {
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
                UUID.randomUUID().toString(),
                responseId,
                chunkId,
                knowledgeBaseId,
                normalizedFeedback,
                trimComment(comment),
                Timestamp.valueOf(now),
                Timestamp.valueOf(now)
        );
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
}
