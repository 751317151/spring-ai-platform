package com.huah.ai.platform.monitor.service;

import com.huah.ai.platform.monitor.alert.AlertEvaluationService;
import com.huah.ai.platform.monitor.alert.AlertEventView;
import com.huah.ai.platform.monitor.alert.AlertmanagerAlertService;
import com.huah.ai.platform.monitor.model.AgentStatView;
import com.huah.ai.platform.monitor.model.AlertsView;
import com.huah.ai.platform.monitor.model.AlertWorkflowHistoryView;
import com.huah.ai.platform.monitor.model.AuditLogView;
import com.huah.ai.platform.monitor.model.EvidenceFeedbackSampleView;
import com.huah.ai.platform.monitor.model.FailureSampleView;
import com.huah.ai.platform.monitor.model.FeedbackOverviewView;
import com.huah.ai.platform.monitor.model.FeedbackSampleView;
import com.huah.ai.platform.monitor.model.HourlyStatView;
import com.huah.ai.platform.monitor.model.ModelStatView;
import com.huah.ai.platform.monitor.model.MonitorOverviewView;
import com.huah.ai.platform.monitor.model.SlowRequestView;
import com.huah.ai.platform.monitor.model.TokenUsageView;
import com.huah.ai.platform.monitor.model.TopUserView;
import com.huah.ai.platform.monitor.model.ToolAuditView;
import com.huah.ai.platform.monitor.model.TraceDetailView;
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

    public MonitorOverviewView getOverview() {
        return monitorMetricsQueryService.getOverview();
    }

    public List<AgentStatView> getAgentStats() {
        return monitorMetricsQueryService.getAgentStats();
    }

    public List<ModelStatView> getModelStats() {
        return monitorMetricsQueryService.getModelStats();
    }

    public TokenUsageView getTokenUsage(String userId) {
        return monitorMetricsQueryService.getTokenUsage(userId);
    }

    public List<AuditLogView> getAuditLogs(int limit, String userId) {
        return monitorTraceQueryService.getAuditLogs(limit, userId);
    }

    public List<TopUserView> getTopUsers() {
        return monitorMetricsQueryService.getTopUsers();
    }

    public List<SlowRequestView> getSlowRequests(int limit) {
        return monitorMetricsQueryService.getSlowRequests(limit);
    }

    public List<FailureSampleView> getFailureSamples(int limit) {
        return monitorMetricsQueryService.getFailureSamples(limit);
    }

    public List<ToolAuditView> getToolAudits(int limit, String userId, String agentType, String toolName) {
        return monitorTraceQueryService.getToolAudits(limit, userId, agentType, toolName);
    }

    public Optional<TraceDetailView> getTraceDetail(String traceId) {
        return monitorTraceQueryService.getTraceDetail(traceId);
    }

    public FeedbackOverviewView getFeedbackOverview() {
        return monitorFeedbackQueryService.getFeedbackOverview();
    }

    public List<FeedbackSampleView> getRecentFeedback(int limit) {
        return monitorFeedbackQueryService.getRecentFeedback(limit);
    }

    public List<EvidenceFeedbackSampleView> getRecentEvidenceFeedback(int limit) {
        return monitorFeedbackQueryService.getRecentEvidenceFeedback(limit);
    }

    public List<HourlyStatView> getHourlyStats() {
        return monitorMetricsQueryService.getHourlyStats();
    }

    public AlertsView getAlerts() {
        List<AlertEventView> alerts = alertmanagerAlertService.fetchActiveAlerts();
        if (alerts.isEmpty()) {
            alerts = alertEvaluationService.evaluate(monitorMetricsQueryService.buildAlertSnapshot());
        }
        Map<String, AlertWorkflowService.AlertWorkflowRecord> workflowMap = alertWorkflowService.getWorkflowMap(
                alerts.stream()
                        .map(AlertEventView::getFingerprint)
                        .filter(value -> value != null && !value.isBlank())
                        .toList()
        );
        List<AlertEventView> merged = alerts.stream()
                .map(item -> mergeWorkflow(item, workflowMap.get(item.getFingerprint())))
                .toList();
        return AlertsView.builder()
                .activeAlerts(merged.stream().filter(a -> !"INFO".equals(a.getLevel())).count())
                .alerts(merged)
                .build();
    }

    public List<AlertWorkflowHistoryView> getAlertWorkflowHistory(String fingerprint, int limit) {
        return alertWorkflowService.getWorkflowHistory(fingerprint, limit).stream()
                .map(item -> AlertWorkflowHistoryView.builder()
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

    private AlertEventView mergeWorkflow(AlertEventView item, AlertWorkflowService.AlertWorkflowRecord workflow) {
        if (workflow == null) {
            return item;
        }
        return AlertEventView.builder()
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
