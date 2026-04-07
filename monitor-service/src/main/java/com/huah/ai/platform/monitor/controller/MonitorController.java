package com.huah.ai.platform.monitor.controller;

import com.huah.ai.platform.common.dto.Result;
import com.huah.ai.platform.monitor.model.AgentStatResponse;
import com.huah.ai.platform.monitor.model.AlertsResponse;
import com.huah.ai.platform.monitor.model.AlertWorkflowHistoryResponse;
import com.huah.ai.platform.monitor.model.AuditLogResponse;
import com.huah.ai.platform.monitor.model.FailureSampleResponse;
import com.huah.ai.platform.monitor.model.EvidenceFeedbackSampleResponse;
import com.huah.ai.platform.monitor.model.FeedbackOverviewResponse;
import com.huah.ai.platform.monitor.model.FeedbackSampleResponse;
import com.huah.ai.platform.monitor.model.HourlyStatResponse;
import com.huah.ai.platform.monitor.model.ModelStatResponse;
import com.huah.ai.platform.monitor.model.MonitorOverviewResponse;
import com.huah.ai.platform.monitor.model.MonitorScreenResponse;
import com.huah.ai.platform.monitor.model.AlertWorkflowUpdateRequest;
import com.huah.ai.platform.monitor.model.SlowRequestResponse;
import com.huah.ai.platform.monitor.model.TraceDetailResponse;
import com.huah.ai.platform.monitor.model.TokenUsageResponse;
import com.huah.ai.platform.monitor.model.TopUserResponse;
import com.huah.ai.platform.monitor.model.ToolAuditResponse;
import com.huah.ai.platform.monitor.service.MonitorQueryService;
import com.huah.ai.platform.monitor.service.MonitorScreenService;
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
    private final MonitorScreenService monitorScreenService;

    @GetMapping("/overview")
    public Result<MonitorOverviewResponse> overview() {
        return Result.ok(monitorQueryService.getOverview());
    }

    @GetMapping("/screen")
    public Result<MonitorScreenResponse> screen() {
        return Result.ok(monitorScreenService.getScreenSnapshot());
    }

    @GetMapping("/by-agent")
    public Result<List<AgentStatResponse>> byAgent() {
        return Result.ok(monitorQueryService.getAgentStats());
    }

    @GetMapping("/by-model")
    public Result<List<ModelStatResponse>> byModel() {
        return Result.ok(monitorQueryService.getModelStats());
    }

    @GetMapping("/token-usage/{userId}")
    public Result<TokenUsageResponse> tokenUsage(@PathVariable(name = "userId") String userId) {
        return Result.ok(monitorQueryService.getTokenUsage(userId));
    }

    @GetMapping("/audit-logs")
    public Result<List<AuditLogResponse>> auditLogs(
            @RequestParam(name = "limit", defaultValue = "20") int limit,
            @RequestParam(name = "userId", required = false) String userId) {
        return Result.ok(monitorQueryService.getAuditLogs(limit, userId));
    }

    @GetMapping("/token-top-users")
    public Result<List<TopUserResponse>> tokenTopUsers() {
        return Result.ok(monitorQueryService.getTopUsers());
    }

    @GetMapping("/slow-requests")
    public Result<List<SlowRequestResponse>> slowRequests(@RequestParam(name = "limit", defaultValue = "10") int limit) {
        return Result.ok(monitorQueryService.getSlowRequests(limit));
    }

    @GetMapping("/failure-samples")
    public Result<List<FailureSampleResponse>> failureSamples(@RequestParam(name = "limit", defaultValue = "10") int limit) {
        return Result.ok(monitorQueryService.getFailureSamples(limit));
    }

    @GetMapping("/tool-audits")
    public Result<List<ToolAuditResponse>> toolAudits(
            @RequestParam(name = "limit", defaultValue = "20") int limit,
            @RequestParam(name = "userId", required = false) String userId,
            @RequestParam(name = "agentType", required = false) String agentType,
            @RequestParam(name = "toolName", required = false) String toolName) {
        return Result.ok(monitorQueryService.getToolAudits(limit, userId, agentType, toolName));
    }

    @GetMapping("/trace/{traceId}")
    public Result<TraceDetailResponse> traceDetail(@PathVariable(name = "traceId") String traceId) {
        return monitorQueryService.getTraceDetail(traceId)
                .map(Result::ok)
                .orElseGet(() -> Result.fail("未找到对应的 Trace 详情"));
    }

    @GetMapping("/feedback/overview")
    public Result<FeedbackOverviewResponse> feedbackOverview() {
        return Result.ok(monitorQueryService.getFeedbackOverview());
    }

    @GetMapping("/feedback/recent")
    public Result<List<FeedbackSampleResponse>> recentFeedback(@RequestParam(name = "limit", defaultValue = "10") int limit) {
        return Result.ok(monitorQueryService.getRecentFeedback(limit));
    }

    @GetMapping("/feedback/evidence")
    public Result<List<EvidenceFeedbackSampleResponse>> recentEvidenceFeedback(@RequestParam(name = "limit", defaultValue = "10") int limit) {
        return Result.ok(monitorQueryService.getRecentEvidenceFeedback(limit));
    }

    @GetMapping("/hourly-stats")
    public Result<List<HourlyStatResponse>> hourlyStats() {
        return Result.ok(monitorQueryService.getHourlyStats());
    }

    @GetMapping("/alerts")
    public Result<AlertsResponse> alerts() {
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
    public Result<List<AlertWorkflowHistoryResponse>> alertWorkflowHistory(
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
