package com.huah.ai.platform.monitor.controller;

import com.huah.ai.platform.common.dto.Result;
import com.huah.ai.platform.monitor.model.AgentStatView;
import com.huah.ai.platform.monitor.model.AlertsView;
import com.huah.ai.platform.monitor.model.AlertWorkflowHistoryView;
import com.huah.ai.platform.monitor.model.AuditLogView;
import com.huah.ai.platform.monitor.model.FailureSampleView;
import com.huah.ai.platform.monitor.model.EvidenceFeedbackSampleView;
import com.huah.ai.platform.monitor.model.FeedbackOverviewView;
import com.huah.ai.platform.monitor.model.FeedbackSampleView;
import com.huah.ai.platform.monitor.model.HourlyStatView;
import com.huah.ai.platform.monitor.model.ModelStatView;
import com.huah.ai.platform.monitor.model.MonitorOverviewView;
import com.huah.ai.platform.monitor.model.AlertWorkflowUpdateRequest;
import com.huah.ai.platform.monitor.model.SlowRequestView;
import com.huah.ai.platform.monitor.model.TraceDetailView;
import com.huah.ai.platform.monitor.model.TokenUsageView;
import com.huah.ai.platform.monitor.model.TopUserView;
import com.huah.ai.platform.monitor.model.ToolAuditView;
import com.huah.ai.platform.monitor.service.MonitorQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/monitor")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class MonitorController {

    private final MonitorQueryService monitorQueryService;

    @GetMapping("/overview")
    public Result<MonitorOverviewView> overview() {
        return Result.ok(monitorQueryService.getOverview());
    }

    @GetMapping("/by-agent")
    public Result<List<AgentStatView>> byAgent() {
        return Result.ok(monitorQueryService.getAgentStats());
    }

    @GetMapping("/by-model")
    public Result<List<ModelStatView>> byModel() {
        return Result.ok(monitorQueryService.getModelStats());
    }

    @GetMapping("/token-usage/{userId}")
    public Result<TokenUsageView> tokenUsage(@PathVariable(name = "userId") String userId) {
        return Result.ok(monitorQueryService.getTokenUsage(userId));
    }

    @GetMapping("/audit-logs")
    public Result<List<AuditLogView>> auditLogs(
            @RequestParam(name = "limit", defaultValue = "20") int limit,
            @RequestParam(name = "userId", required = false) String userId) {
        return Result.ok(monitorQueryService.getAuditLogs(limit, userId));
    }

    @GetMapping("/token-top-users")
    public Result<List<TopUserView>> tokenTopUsers() {
        return Result.ok(monitorQueryService.getTopUsers());
    }

    @GetMapping("/slow-requests")
    public Result<List<SlowRequestView>> slowRequests(@RequestParam(name = "limit", defaultValue = "10") int limit) {
        return Result.ok(monitorQueryService.getSlowRequests(limit));
    }

    @GetMapping("/failure-samples")
    public Result<List<FailureSampleView>> failureSamples(@RequestParam(name = "limit", defaultValue = "10") int limit) {
        return Result.ok(monitorQueryService.getFailureSamples(limit));
    }

    @GetMapping("/tool-audits")
    public Result<List<ToolAuditView>> toolAudits(
            @RequestParam(name = "limit", defaultValue = "20") int limit,
            @RequestParam(name = "userId", required = false) String userId,
            @RequestParam(name = "agentType", required = false) String agentType,
            @RequestParam(name = "toolName", required = false) String toolName) {
        return Result.ok(monitorQueryService.getToolAudits(limit, userId, agentType, toolName));
    }

    @GetMapping("/trace/{traceId}")
    public Result<TraceDetailView> traceDetail(@PathVariable(name = "traceId") String traceId) {
        return monitorQueryService.getTraceDetail(traceId)
                .map(Result::ok)
                .orElseGet(() -> Result.fail("未找到对应的 Trace 详情"));
    }

    @GetMapping("/feedback/overview")
    public Result<FeedbackOverviewView> feedbackOverview() {
        return Result.ok(monitorQueryService.getFeedbackOverview());
    }

    @GetMapping("/feedback/recent")
    public Result<List<FeedbackSampleView>> recentFeedback(@RequestParam(name = "limit", defaultValue = "10") int limit) {
        return Result.ok(monitorQueryService.getRecentFeedback(limit));
    }

    @GetMapping("/feedback/evidence")
    public Result<List<EvidenceFeedbackSampleView>> recentEvidenceFeedback(@RequestParam(name = "limit", defaultValue = "10") int limit) {
        return Result.ok(monitorQueryService.getRecentEvidenceFeedback(limit));
    }

    @GetMapping("/hourly-stats")
    public Result<List<HourlyStatView>> hourlyStats() {
        return Result.ok(monitorQueryService.getHourlyStats());
    }

    @GetMapping("/alerts")
    public Result<AlertsView> alerts() {
        return Result.ok(monitorQueryService.getAlerts());
    }

    @PostMapping("/alerts/{fingerprint}/workflow")
    public Result<String> updateAlertWorkflow(
            @PathVariable(name = "fingerprint") String fingerprint,
            @RequestBody AlertWorkflowUpdateRequest request) {
        monitorQueryService.updateAlertWorkflow(fingerprint, request.getWorkflowStatus(), request.getWorkflowNote(), request.getSilencedUntil());
        return Result.ok("告警流转状态已更新");
    }

    @GetMapping("/alerts/{fingerprint}/history")
    public Result<List<AlertWorkflowHistoryView>> alertWorkflowHistory(
            @PathVariable(name = "fingerprint") String fingerprint,
            @RequestParam(name = "limit", defaultValue = "10") int limit) {
        return Result.ok(monitorQueryService.getAlertWorkflowHistory(fingerprint, limit));
    }

    @GetMapping(value = "/export/slow-requests", produces = "text/csv")
    public ResponseEntity<String> exportSlowRequests(@RequestParam(name = "limit", defaultValue = "100") int limit) {
        return csvResponse("slow-requests.csv", monitorQueryService.exportSlowRequestsCsv(limit));
    }

    @GetMapping(value = "/export/failure-samples", produces = "text/csv")
    public ResponseEntity<String> exportFailureSamples(@RequestParam(name = "limit", defaultValue = "100") int limit) {
        return csvResponse("failure-samples.csv", monitorQueryService.exportFailureSamplesCsv(limit));
    }

    @GetMapping(value = "/export/feedback", produces = "text/csv")
    public ResponseEntity<String> exportFeedback(@RequestParam(name = "limit", defaultValue = "100") int limit) {
        return csvResponse("feedback.csv", monitorQueryService.exportFeedbackCsv(limit));
    }

    @GetMapping(value = "/export/evidence-feedback", produces = "text/csv")
    public ResponseEntity<String> exportEvidenceFeedback(@RequestParam(name = "limit", defaultValue = "100") int limit) {
        return csvResponse("evidence-feedback.csv", monitorQueryService.exportEvidenceFeedbackCsv(limit));
    }

    @GetMapping(value = "/export/top-users", produces = "text/csv")
    public ResponseEntity<String> exportTopUsers() {
        return csvResponse("top-users.csv", monitorQueryService.exportTopUsersCsv());
    }

    private ResponseEntity<String> csvResponse(String filename, String content) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(content);
    }
}
