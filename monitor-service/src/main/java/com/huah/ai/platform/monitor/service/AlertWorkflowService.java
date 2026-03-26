package com.huah.ai.platform.monitor.service;

import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AlertWorkflowService {

    private final JdbcTemplate jdbcTemplate;

    public Map<String, AlertWorkflowRecord> getWorkflowMap(List<String> fingerprints) {
        if (fingerprints == null || fingerprints.isEmpty()) {
            return Collections.emptyMap();
        }
        String placeholders = String.join(",", Collections.nCopies(fingerprints.size(), "?"));
        List<AlertWorkflowRecord> rows = jdbcTemplate.query(
                "SELECT fingerprint, workflow_status, workflow_note, updated_at FROM ai_alert_workflow WHERE fingerprint IN (" + placeholders + ")",
                (rs, rowNum) -> AlertWorkflowRecord.builder()
                        .fingerprint(rs.getString("fingerprint"))
                        .workflowStatus(rs.getString("workflow_status"))
                        .workflowNote(rs.getString("workflow_note"))
                        .silencedUntil(toLocalDateTime(rs.getTimestamp("silenced_until")))
                        .updatedAt(toLocalDateTime(rs.getTimestamp("updated_at")))
                        .build(),
                fingerprints.toArray()
        );
        return rows.stream().collect(Collectors.toMap(AlertWorkflowRecord::getFingerprint, Function.identity()));
    }

    public List<AlertWorkflowHistoryRecord> getWorkflowHistory(String fingerprint, int limit) {
        return jdbcTemplate.query(
                "SELECT fingerprint, workflow_status, workflow_note, silenced_until, created_at " +
                        "FROM ai_alert_workflow_history WHERE fingerprint = ? ORDER BY created_at DESC LIMIT ?",
                (rs, rowNum) -> AlertWorkflowHistoryRecord.builder()
                        .fingerprint(rs.getString("fingerprint"))
                        .workflowStatus(rs.getString("workflow_status"))
                        .workflowNote(rs.getString("workflow_note"))
                        .silencedUntil(toLocalDateTime(rs.getTimestamp("silenced_until")))
                        .createdAt(toLocalDateTime(rs.getTimestamp("created_at")))
                        .build(),
                fingerprint,
                limit
        );
    }

    public void saveWorkflow(String fingerprint, String workflowStatus, String workflowNote, LocalDateTime silencedUntil) {
        jdbcTemplate.update("""
                INSERT INTO ai_alert_workflow (fingerprint, workflow_status, workflow_note, silenced_until, updated_at)
                VALUES (?, ?, ?, ?, NOW())
                ON CONFLICT (fingerprint) DO UPDATE SET
                    workflow_status = EXCLUDED.workflow_status,
                    workflow_note = EXCLUDED.workflow_note,
                    silenced_until = EXCLUDED.silenced_until,
                    updated_at = NOW()
                """,
                fingerprint,
                workflowStatus,
                workflowNote,
                toTimestamp(silencedUntil)
        );
        jdbcTemplate.update(
                "INSERT INTO ai_alert_workflow_history (fingerprint, workflow_status, workflow_note, silenced_until, created_at) VALUES (?, ?, ?, ?, NOW())",
                fingerprint,
                workflowStatus,
                workflowNote,
                toTimestamp(silencedUntil)
        );
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp != null ? timestamp.toLocalDateTime() : null;
    }

    private Timestamp toTimestamp(LocalDateTime value) {
        return value != null ? Timestamp.valueOf(value) : null;
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
