package com.huah.ai.platform.monitor.service;

import com.huah.ai.platform.monitor.alert.AlertEvaluationService;
import com.huah.ai.platform.monitor.alert.AlertEventResponse;
import com.huah.ai.platform.monitor.alert.AlertmanagerAlertService;
import com.huah.ai.platform.monitor.model.AgentStatResponse;
import com.huah.ai.platform.monitor.model.AlertsResponse;
import com.huah.ai.platform.monitor.model.AlertWorkflowHistoryResponse;
import com.huah.ai.platform.monitor.model.AuditLogResponse;
import com.huah.ai.platform.monitor.model.EvidenceFeedbackSampleResponse;
import com.huah.ai.platform.monitor.model.FailureSampleResponse;
import com.huah.ai.platform.monitor.model.FeedbackOverviewResponse;
import com.huah.ai.platform.monitor.model.FeedbackSampleResponse;
import com.huah.ai.platform.monitor.model.HourlyStatResponse;
import com.huah.ai.platform.monitor.model.ModelStatResponse;
import com.huah.ai.platform.monitor.model.MonitorOverviewResponse;
import com.huah.ai.platform.monitor.model.RegionHeatResponse;
import com.huah.ai.platform.monitor.model.SlowRequestResponse;
import com.huah.ai.platform.monitor.model.TokenUsageResponse;
import com.huah.ai.platform.monitor.model.TopUserResponse;
import com.huah.ai.platform.monitor.model.ToolAuditResponse;
import com.huah.ai.platform.monitor.model.TraceDetailResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MonitorQueryService {

    private final MonitorMetricsQueryService monitorMetricsQueryService;
    private final MonitorTraceQueryService monitorTraceQueryService;
    private final MonitorFeedbackQueryService monitorFeedbackQueryService;
    private final MonitorCsvExportService monitorCsvExportService;
    private final AlertEvaluationService alertEvaluationService;
    private final AlertmanagerAlertService alertmanagerAlertService;
    private final AlertWorkflowService alertWorkflowService;

    public MonitorOverviewResponse getOverview() {
        return monitorMetricsQueryService.getOverview();
    }

    public List<AgentStatResponse> getAgentStats() {
        return monitorMetricsQueryService.getAgentStats();
    }

    public List<ModelStatResponse> getModelStats() {
        return monitorMetricsQueryService.getModelStats();
    }

    public TokenUsageResponse getTokenUsage(String userId) {
        return monitorMetricsQueryService.getTokenUsage(userId);
    }

    public List<AuditLogResponse> getAuditLogs(int limit, String userId) {
        return monitorTraceQueryService.getAuditLogs(limit, userId);
    }

    public List<TopUserResponse> getTopUsers() {
        return monitorMetricsQueryService.getTopUsers();
    }

    public List<RegionHeatResponse> getRegionHeat() {
        return monitorMetricsQueryService.getRegionHeat();
    }

    public List<SlowRequestResponse> getSlowRequests(int limit) {
        return monitorMetricsQueryService.getSlowRequests(limit);
    }

    public List<FailureSampleResponse> getFailureSamples(int limit) {
        return monitorMetricsQueryService.getFailureSamples(limit);
    }

    public List<ToolAuditResponse> getToolAudits(int limit, String userId, String agentType, String toolName) {
        return monitorTraceQueryService.getToolAudits(limit, userId, agentType, toolName);
    }

    public Optional<TraceDetailResponse> getTraceDetail(String traceId) {
        return monitorTraceQueryService.getTraceDetail(traceId);
    }

    public FeedbackOverviewResponse getFeedbackOverview() {
        return monitorFeedbackQueryService.getFeedbackOverview();
    }

    public List<FeedbackSampleResponse> getRecentFeedback(int limit) {
        return monitorFeedbackQueryService.getRecentFeedback(limit);
    }

    public List<EvidenceFeedbackSampleResponse> getRecentEvidenceFeedback(int limit) {
        return monitorFeedbackQueryService.getRecentEvidenceFeedback(limit);
    }

    public List<HourlyStatResponse> getHourlyStats() {
        return monitorMetricsQueryService.getHourlyStats();
    }

    public AlertsResponse getAlerts() {
        List<AlertEventResponse> alerts = alertmanagerAlertService.fetchActiveAlerts();
        if (alerts.isEmpty()) {
            alerts = alertEvaluationService.evaluate(monitorMetricsQueryService.buildAlertSnapshot());
        }
        Map<String, AlertWorkflowService.AlertWorkflowRecord> workflowMap = alertWorkflowService.getWorkflowMap(
                alerts.stream()
                        .map(AlertEventResponse::getFingerprint)
                        .filter(value -> value != null && !value.isBlank())
                        .toList()
        );
        List<AlertEventResponse> merged = alerts.stream()
                .map(item -> mergeWorkflow(item, workflowMap.get(item.getFingerprint())))
                .toList();
        return AlertsResponse.builder()
                .activeAlerts(merged.stream().filter(a -> !"INFO".equals(a.getLevel())).count())
                .alerts(merged)
                .build();
    }

    public List<AlertWorkflowHistoryResponse> getAlertWorkflowHistory(String fingerprint, int limit) {
        return alertWorkflowService.getWorkflowHistory(fingerprint, limit).stream()
                .map(item -> AlertWorkflowHistoryResponse.builder()
                        .fingerprint(item.getFingerprint())
                        .workflowStatus(item.getWorkflowStatus())
                        .workflowNote(item.getWorkflowNote())
                        .silencedUntil(item.getSilencedUntil())
                        .createdAt(item.getCreatedAt())
                        .build())
                .toList();
    }

    public void updateAlertWorkflow(String fingerprint, String workflowStatus, String workflowNote, String silencedUntil) {
        alertWorkflowService.saveWorkflow(fingerprint, workflowStatus, workflowNote, parseDateTime(silencedUntil));
    }

    public String exportSlowRequestsCsv(int limit) {
        return monitorCsvExportService.exportSlowRequestsCsv(limit);
    }

    public String exportFailureSamplesCsv(int limit) {
        return monitorCsvExportService.exportFailureSamplesCsv(limit);
    }

    public String exportFeedbackCsv(int limit) {
        return monitorCsvExportService.exportFeedbackCsv(limit);
    }

    public String exportEvidenceFeedbackCsv(int limit) {
        return monitorCsvExportService.exportEvidenceFeedbackCsv(limit);
    }

    public String exportTopUsersCsv() {
        return monitorCsvExportService.exportTopUsersCsv();
    }

    private AlertEventResponse mergeWorkflow(AlertEventResponse item, AlertWorkflowService.AlertWorkflowRecord workflow) {
        if (workflow == null) {
            return item;
        }
        return AlertEventResponse.builder()
                .level(item.getLevel())
                .type(item.getType())
                .message(item.getMessage())
                .time(item.getTime())
                .source(item.getSource())
                .status(item.getStatus())
                .fingerprint(item.getFingerprint())
                .silenceUrl(item.getSilenceUrl())
                .workflowStatus(workflow.getWorkflowStatus())
                .workflowNote(workflow.getWorkflowNote())
                .workflowUpdatedAt(workflow.getUpdatedAt())
                .silencedUntil(workflow.getSilencedUntil())
                .labels(item.getLabels())
                .build();
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return OffsetDateTime.parse(value).toLocalDateTime();
    }
}

