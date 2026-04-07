package com.huah.ai.platform.monitor.service;

import com.huah.ai.platform.common.persistence.audit.AiAlertWorkflowEntity;
import com.huah.ai.platform.common.persistence.audit.AiAlertWorkflowHistoryEntity;
import com.huah.ai.platform.common.persistence.audit.AiAlertWorkflowHistoryMapper;
import com.huah.ai.platform.common.persistence.audit.AiAlertWorkflowMapper;
import com.huah.ai.platform.common.util.SnowflakeIdGenerator;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AlertWorkflowService {

    private final AiAlertWorkflowMapper alertWorkflowMapper;
    private final AiAlertWorkflowHistoryMapper alertWorkflowHistoryMapper;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    public Map<String, AlertWorkflowRecord> getWorkflowMap(List<String> fingerprints) {
        if (fingerprints == null || fingerprints.isEmpty()) {
            return Collections.emptyMap();
        }
        return alertWorkflowMapper.selectByFingerprints(fingerprints).stream()
                .map(this::toRecord)
                .collect(Collectors.toMap(AlertWorkflowRecord::getFingerprint, Function.identity()));
    }

    public List<AlertWorkflowHistoryRecord> getWorkflowHistory(String fingerprint, int limit) {
        return alertWorkflowHistoryMapper.selectByFingerprintLimit(fingerprint, limit).stream()
                .map(this::toHistoryRecord)
                .toList();
    }

    public void saveWorkflow(String fingerprint, String workflowStatus, String workflowNote, LocalDateTime silencedUntil) {
        LocalDateTime now = LocalDateTime.now();
        alertWorkflowMapper.upsert(AiAlertWorkflowEntity.builder()
                .fingerprint(fingerprint)
                .workflowStatus(workflowStatus)
                .workflowNote(workflowNote)
                .silencedUntil(silencedUntil)
                .createdAt(now)
                .updatedAt(now)
                .build());
        alertWorkflowHistoryMapper.insert(AiAlertWorkflowHistoryEntity.builder()
                .id(snowflakeIdGenerator.nextLongId())
                .fingerprint(fingerprint)
                .workflowStatus(workflowStatus)
                .workflowNote(workflowNote)
                .silencedUntil(silencedUntil)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    private AlertWorkflowRecord toRecord(AiAlertWorkflowEntity entity) {
        return AlertWorkflowRecord.builder()
                .fingerprint(entity.getFingerprint())
                .workflowStatus(entity.getWorkflowStatus())
                .workflowNote(entity.getWorkflowNote())
                .silencedUntil(entity.getSilencedUntil())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private AlertWorkflowHistoryRecord toHistoryRecord(AiAlertWorkflowHistoryEntity entity) {
        return AlertWorkflowHistoryRecord.builder()
                .fingerprint(entity.getFingerprint())
                .workflowStatus(entity.getWorkflowStatus())
                .workflowNote(entity.getWorkflowNote())
                .silencedUntil(entity.getSilencedUntil())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    @Value
    @Builder
    public static class AlertWorkflowRecord {
        String fingerprint;
        String workflowStatus;
        String workflowNote;
        LocalDateTime silencedUntil;
        LocalDateTime updatedAt;
    }

    @Value
    @Builder
    public static class AlertWorkflowHistoryRecord {
        String fingerprint;
        String workflowStatus;
        String workflowNote;
        LocalDateTime silencedUntil;
        LocalDateTime createdAt;
    }
}
