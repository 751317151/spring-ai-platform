package com.huah.ai.platform.agent.audit;

import com.huah.ai.platform.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ResponseFeedbackService {

    private final AiAuditLogMapper auditLogMapper;
    private final AiResponseFeedbackMapper feedbackMapper;

    public void submitAgentFeedback(String userId, String responseId, String feedback, String comment) {
        if (responseId == null || responseId.isBlank()) {
            throw new BizException("responseId不能为空");
        }

        String normalizedFeedback = normalizeFeedback(feedback);
        AiAuditLog log = auditLogMapper.selectById(responseId);
        if (log == null) {
            throw new BizException("未找到对应回答记录");
        }
        if (log.getUserId() != null && !log.getUserId().equals(userId)) {
            throw new BizException("无权提交该回答反馈");
        }

        AiResponseFeedback existing = feedbackMapper.selectByResponseId(responseId);
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            feedbackMapper.insert(AiResponseFeedback.builder()
                    .id(UUID.randomUUID().toString())
                    .responseId(responseId)
                    .sourceType("agent")
                    .feedback(normalizedFeedback)
                    .comment(trimComment(comment))
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
            return;
        }

        existing.setFeedback(normalizedFeedback);
        existing.setComment(trimComment(comment));
        existing.setUpdatedAt(now);
        feedbackMapper.updateById(existing);
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
}
