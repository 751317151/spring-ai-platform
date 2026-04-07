package com.huah.ai.platform.rag.service;

import com.huah.ai.platform.common.exception.BizException;
import com.huah.ai.platform.common.persistence.audit.AiAuditLogEntity;
import com.huah.ai.platform.common.persistence.audit.AiAuditLogMapper;
import com.huah.ai.platform.common.persistence.audit.AiEvidenceFeedbackEntity;
import com.huah.ai.platform.common.persistence.audit.AiEvidenceFeedbackMapper;
import com.huah.ai.platform.common.persistence.audit.AiResponseFeedbackEntity;
import com.huah.ai.platform.common.persistence.audit.AiResponseFeedbackMapper;
import com.huah.ai.platform.common.trace.TraceIdContext;
import com.huah.ai.platform.common.util.SnowflakeIdGenerator;
import com.huah.ai.platform.common.web.RequestOrigin;
import com.huah.ai.platform.rag.model.RagEvaluationOverview;
import com.huah.ai.platform.rag.model.RagEvaluationSample;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RagAuditService {

    private final AiAuditLogMapper auditLogMapper;
    private final AiResponseFeedbackMapper responseFeedbackMapper;
    private final AiEvidenceFeedbackMapper evidenceFeedbackMapper;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    public Long saveQueryLog(
            String userId,
            Long knowledgeBaseId,
            String question,
            String answer,
            long latencyMs,
            boolean success,
            String errorMessage,
            RequestOrigin requestOrigin) {
        Long responseId = snowflakeIdGenerator.nextLongId();
        LocalDateTime now = LocalDateTime.now();
        auditLogMapper.insert(AiAuditLogEntity.builder()
                .id(responseId)
                .userId(userId)
                .agentType("rag")
                .userMessage(truncate(question, 500))
                .aiResponse(truncate(answer, 500))
                .latencyMs(latencyMs)
                .success(success)
                .errorMessage(errorMessage)
                .clientIp(requestOrigin != null ? requestOrigin.getClientIp() : null)
                .country(requestOrigin != null ? requestOrigin.getCountry() : null)
                .province(requestOrigin != null ? requestOrigin.getProvince() : null)
                .city(requestOrigin != null ? requestOrigin.getCity() : null)
                .sessionId(knowledgeBaseId != null ? String.valueOf(knowledgeBaseId) : null)
                .traceId(TraceIdContext.currentTraceId())
                .createdAt(now)
                .updatedAt(now)
                .build());
        return responseId;
    }

    public void submitFeedback(
            String userId,
            Long responseId,
            Long knowledgeBaseId,
            String feedback,
            String comment) {
        if (responseId == null) {
            throw new BizException("responseId不能为空");
        }

        AiAuditLogEntity log = auditLogMapper.selectById(responseId);
        if (log == null) {
            throw new BizException("未找到对应回答记录");
        }
        if (log.getUserId() != null && !log.getUserId().equals(userId)) {
            throw new BizException("无权提交该回答反馈");
        }

        String normalizedFeedback = normalizeFeedback(feedback);
        String normalizedComment = trimComment(comment);
        LocalDateTime now = LocalDateTime.now();
        AiResponseFeedbackEntity existing = responseFeedbackMapper.selectByResponseId(responseId);
        if (existing == null) {
            responseFeedbackMapper.insert(AiResponseFeedbackEntity.builder()
                    .id(snowflakeIdGenerator.nextLongId())
                    .responseId(responseId)
                    .sourceType("rag")
                    .knowledgeBaseId(knowledgeBaseId)
                    .feedback(normalizedFeedback)
                    .comment(normalizedComment)
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
            return;
        }

        existing.setKnowledgeBaseId(knowledgeBaseId);
        existing.setFeedback(normalizedFeedback);
        existing.setComment(normalizedComment);
        existing.setUpdatedAt(now);
        responseFeedbackMapper.updateById(existing);
    }

    public void submitEvidenceFeedback(
            String userId,
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

        AiAuditLogEntity log = auditLogMapper.selectById(responseId);
        if (log == null) {
            throw new BizException("未找到对应回答记录");
        }
        if (!userId.equals(log.getUserId())) {
            throw new BizException("无权提交该证据反馈");
        }

        String normalizedFeedback = normalizeFeedback(feedback);
        String normalizedComment = trimComment(comment);
        LocalDateTime now = LocalDateTime.now();
        AiEvidenceFeedbackEntity existing = evidenceFeedbackMapper.selectByResponseIdAndChunkId(responseId, chunkId);
        if (existing == null) {
            evidenceFeedbackMapper.insert(AiEvidenceFeedbackEntity.builder()
                    .id(snowflakeIdGenerator.nextLongId())
                    .responseId(responseId)
                    .chunkId(chunkId)
                    .knowledgeBaseId(knowledgeBaseId)
                    .feedback(normalizedFeedback)
                    .comment(normalizedComment)
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
            return;
        }

        existing.setKnowledgeBaseId(knowledgeBaseId);
        existing.setFeedback(normalizedFeedback);
        existing.setComment(normalizedComment);
        existing.setUpdatedAt(now);
        evidenceFeedbackMapper.updateById(existing);
    }

    public RagEvaluationOverview getEvaluationOverview(Long knowledgeBaseId) {
        try {
            long totalQueries = auditLogMapper.countRagQueries(knowledgeBaseId);
            Map<String, Object> feedbackStats =
                    responseFeedbackMapper.countStatsBySourceTypeAndKnowledgeBaseId("rag", knowledgeBaseId);
            Map<String, Object> evidenceStats = evidenceFeedbackMapper.countStatsByKnowledgeBaseId(knowledgeBaseId);

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
            return auditLogMapper.selectLowRatedRagSamples(knowledgeBaseId, limit).stream()
                    .map(this::toSample)
                    .toList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private RagEvaluationSample toSample(Map<String, Object> row) {
        Object createdAt = row.get("createdAt");
        LocalDateTime createdAtValue =
                createdAt instanceof LocalDateTime localDateTime ? localDateTime : null;
        return RagEvaluationSample.builder()
                .responseId(getLong(row, "responseId"))
                .userId(stringValue(row.get("userId")))
                .knowledgeBaseId(getNullableLong(row, "knowledgeBaseId"))
                .question(stringValue(row.get("question")))
                .answer(stringValue(row.get("answer")))
                .feedback(stringValue(row.get("feedback")))
                .comment(stringValue(row.get("comment")))
                .evidenceNegativeCount(getLong(row, "evidenceNegativeCount"))
                .createdAt(createdAtValue)
                .build();
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

    private String stringValue(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private Long getNullableLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof Number number ? number.longValue() : null;
    }

    private long getLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof Number number ? number.longValue() : 0L;
    }
}
