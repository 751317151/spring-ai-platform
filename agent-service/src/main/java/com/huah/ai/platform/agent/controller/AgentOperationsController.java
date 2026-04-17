package com.huah.ai.platform.agent.controller;

import com.huah.ai.platform.agent.dto.AgentAccessOverviewResponse;
import com.huah.ai.platform.agent.dto.AgentArchivedTraceLookupResponse;
import com.huah.ai.platform.agent.dto.AgentDiagnosticsResponse;
import com.huah.ai.platform.agent.dto.AgentLogArchiveDetailResponse;
import com.huah.ai.platform.agent.dto.AgentLogArchivePreviewResponse;
import com.huah.ai.platform.agent.dto.AgentLogCleanupRequest;
import com.huah.ai.platform.agent.dto.AgentLogCleanupResponse;
import com.huah.ai.platform.agent.dto.AgentLogLifecycleSummaryResponse;
import com.huah.ai.platform.agent.dto.AgentMetadataResponse;
import com.huah.ai.platform.agent.dto.AgentWorkbenchCompareResponse;
import com.huah.ai.platform.agent.dto.AgentWorkbenchSummaryResponse;
import com.huah.ai.platform.agent.dto.McpServerListResponse;
import com.huah.ai.platform.agent.dto.MultiAgentTraceRecoverRequest;
import com.huah.ai.platform.agent.dto.MultiAgentTraceResponse;
import com.huah.ai.platform.agent.dto.ToolAuditLogResponse;
import com.huah.ai.platform.agent.dto.ToolSecurityOverviewResponse;
import com.huah.ai.platform.agent.multi.MultiAgentExecutionListener;
import com.huah.ai.platform.agent.multi.MultiAgentExecutionTrace;
import com.huah.ai.platform.agent.multi.MultiAgentTraceService;
import com.huah.ai.platform.agent.security.AgentAccessChecker;
import com.huah.ai.platform.agent.service.AgentAccessOverviewService;
import com.huah.ai.platform.agent.service.AgentDiagnosticsFacadeService;
import com.huah.ai.platform.agent.service.AgentLogArchiveService;
import com.huah.ai.platform.agent.service.AgentLogLifecycleService;
import com.huah.ai.platform.agent.service.AgentMetadataService;
import com.huah.ai.platform.agent.service.AgentToolAuditQueryService;
import com.huah.ai.platform.agent.service.AgentRuntimeIsolationService;
import com.huah.ai.platform.agent.service.AgentWorkbenchService;
import com.huah.ai.platform.agent.service.McpServerCatalogService;
import com.huah.ai.platform.common.dto.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(AgentApiConstants.BASE_PATH)
@RequiredArgsConstructor
@Tag(name = "Agent Operations", description = "Agent 运维 - MCP 服务器、诊断、工作台、日志生命周期、Multi-Agent 追踪")
public class AgentOperationsController {

    private final MultiAgentTraceService multiAgentTraceService;
    private final AgentAccessChecker accessChecker;
    private final McpServerCatalogService mcpServerCatalogService;
    private final AgentAccessOverviewService agentAccessOverviewService;
    private final AgentWorkbenchService agentWorkbenchService;
    private final AgentLogLifecycleService agentLogLifecycleService;
    private final AgentLogArchiveService agentLogArchiveService;
    private final AgentMetadataService agentMetadataService;
    private final AgentRuntimeIsolationService agentRuntimeIsolationService;
    private final AgentToolAuditQueryService agentToolAuditQueryService;
    private final AgentDiagnosticsFacadeService agentDiagnosticsFacadeService;
    private final AgentControllerSupport controllerSupport;

    @GetMapping("/mcp/servers")
    public Result<McpServerListResponse> listMcpServers() {
        return Result.ok(mcpServerCatalogService.listServers());
    }

    @GetMapping("/metadata")
    public Result<AgentMetadataResponse> listAgentMetadata() {
        return Result.ok(agentMetadataService.list());
    }

    @GetMapping("/mcp/servers/{agentType}")
    public Result<McpServerListResponse> listMcpServersByAgent(
            @PathVariable(name = "agentType") String agentType,
            HttpServletRequest request) {
        String deny = controllerSupport.checkAgentAccess(agentType, request, accessChecker, "READ");
        if (deny != null) {
            return Result.fail(AgentApiConstants.HTTP_FORBIDDEN, deny);
        }
        return Result.ok(mcpServerCatalogService.listServers(agentType));
    }

    @GetMapping("/tools/audit")
    public Result<List<ToolAuditLogResponse>> listToolAuditLogs(
            @RequestParam(name = "agentType", required = false) String agentType,
            @RequestParam(name = "toolName", required = false) String toolName,
            @RequestParam(name = "traceId", required = false) String traceId,
            @RequestParam(name = "limit", defaultValue = "50") Integer limit,
            HttpServletRequest request) {
        String userId = controllerSupport.currentUserId(request);
        int safeLimit = controllerSupport.safeLimit(limit, 50, 1, 200);
        return Result.ok(agentToolAuditQueryService.listRecent(userId, agentType, toolName, traceId, safeLimit));
    }

    @GetMapping("/tools/security/{agentType}")
    public Result<ToolSecurityOverviewResponse> getToolSecurityOverview(
            @PathVariable(name = "agentType") String agentType,
            HttpServletRequest request) {
        String deny = controllerSupport.checkAgentAccess(agentType, request, accessChecker, "READ");
        if (deny != null) {
            return Result.fail(AgentApiConstants.HTTP_FORBIDDEN, deny);
        }
        return Result.ok(agentDiagnosticsFacadeService.buildToolSecurityOverview(agentType));
    }

    @GetMapping("/diagnostics/{agentType}")
    public Result<AgentDiagnosticsResponse> getAgentDiagnostics(
            @PathVariable(name = "agentType") String agentType,
            HttpServletRequest request) {
        AgentRequestContext context = controllerSupport.resolveContext(request);
        String deny = accessChecker.checkPermission(agentType, context.getRoles(), context.getDepartment(), "READ");
        if (deny != null) {
            return Result.fail(AgentApiConstants.HTTP_FORBIDDEN, deny);
        }
        return Result.ok(agentDiagnosticsFacadeService.buildDiagnostics(agentType, context.getUserId()));
    }

    @GetMapping("/access/{agentType}")
    public Result<AgentAccessOverviewResponse> getAgentAccessOverview(
            @PathVariable(name = "agentType") String agentType,
            HttpServletRequest request) {
        String deny = controllerSupport.checkAgentAccess(agentType, request, accessChecker, "READ");
        if (deny != null) {
            return Result.fail(AgentApiConstants.HTTP_FORBIDDEN, deny);
        }
        return Result.ok(agentAccessOverviewService.build(agentType));
    }

    @GetMapping("/workbench/{agentType}")
    public Result<AgentWorkbenchSummaryResponse> getAgentWorkbenchSummary(
            @PathVariable(name = "agentType") String agentType,
            HttpServletRequest request) {
        String deny = controllerSupport.checkAgentAccess(agentType, request, accessChecker, "READ");
        if (deny != null) {
            return Result.fail(AgentApiConstants.HTTP_FORBIDDEN, deny);
        }
        return Result.ok(agentWorkbenchService.build(agentType));
    }

    @GetMapping("/workbench/compare")
    public Result<AgentWorkbenchCompareResponse> compareAgentWorkbench(
            @RequestParam(name = "leftAgent") String leftAgent,
            @RequestParam(name = "rightAgent") String rightAgent,
            HttpServletRequest request) {
        String leftDeny = controllerSupport.checkAgentAccess(leftAgent, request, accessChecker, "READ");
        if (leftDeny != null) {
            return Result.fail(AgentApiConstants.HTTP_FORBIDDEN, leftDeny);
        }
        String rightDeny = controllerSupport.checkAgentAccess(rightAgent, request, accessChecker, "READ");
        if (rightDeny != null) {
            return Result.fail(AgentApiConstants.HTTP_FORBIDDEN, rightDeny);
        }
        return Result.ok(agentWorkbenchService.compare(leftAgent, rightAgent));
    }

    @GetMapping("/logs/lifecycle/{agentType}")
    public Result<AgentLogLifecycleSummaryResponse> getAgentLogLifecycleSummary(
            @PathVariable(name = "agentType") String agentType,
            HttpServletRequest request) {
        String deny = controllerSupport.checkAgentAccess(agentType, request, accessChecker, "READ");
        if (deny != null) {
            return Result.fail(AgentApiConstants.HTTP_FORBIDDEN, deny);
        }
        return Result.ok(agentLogLifecycleService.buildSummary(agentType));
    }

    @GetMapping("/logs/lifecycle/{agentType}/archive/latest")
    public Result<AgentLogArchiveDetailResponse> getLatestAgentLogArchive(
            @PathVariable(name = "agentType") String agentType,
            HttpServletRequest request) {
        String deny = controllerSupport.checkAgentAccess(agentType, request, accessChecker, "READ");
        if (deny != null) {
            return Result.fail(AgentApiConstants.HTTP_FORBIDDEN, deny);
        }
        return Result.ok(agentLogArchiveService.loadLatestManifest(agentType));
    }

    @GetMapping("/logs/lifecycle/{agentType}/archive/latest/preview")
    public Result<AgentLogArchivePreviewResponse> previewLatestAgentLogArchive(
            @PathVariable(name = "agentType") String agentType,
            @RequestParam(name = "artifactType") String artifactType,
            @RequestParam(name = "limit", defaultValue = "5") Integer limit,
            HttpServletRequest request) {
        String deny = controllerSupport.checkAgentAccess(agentType, request, accessChecker, "READ");
        if (deny != null) {
            return Result.fail(AgentApiConstants.HTTP_FORBIDDEN, deny);
        }
        int safeLimit = controllerSupport.safeLimit(limit, 5, 1, 20);
        return Result.ok(agentLogArchiveService.previewLatestArtifact(agentType, artifactType, safeLimit));
    }

    @GetMapping("/logs/lifecycle/{agentType}/archive/latest/trace")
    public Result<AgentArchivedTraceLookupResponse> findLatestArchivedTrace(
            @PathVariable(name = "agentType") String agentType,
            @RequestParam(name = "traceId") String traceId,
            HttpServletRequest request) {
        String deny = controllerSupport.checkAgentAccess(agentType, request, accessChecker);
        if (deny != null) {
            return Result.fail(AgentApiConstants.HTTP_FORBIDDEN, deny);
        }
        return Result.ok(agentLogArchiveService.findArchivedTrace(agentType, traceId));
    }

    @PostMapping("/logs/lifecycle/{agentType}/archive/latest/trace/replay")
    public Result<MultiAgentTraceResponse> replayLatestArchivedTrace(
            @PathVariable(name = "agentType") String agentType,
            @RequestParam(name = "traceId") String traceId,
            HttpServletRequest request) {
        AgentRequestContext context = controllerSupport.resolveContext(request);
        String deny = accessChecker.checkPermission(agentType, context.getRoles(), context.getDepartment(), "EXECUTE");
        if (deny != null) {
            return Result.fail(AgentApiConstants.HTTP_FORBIDDEN, deny);
        }
        MultiAgentExecutionTrace archivedTrace = agentLogArchiveService.loadArchivedTraceRecord(agentType, traceId);
        if (archivedTrace == null) {
            return Result.fail(404, AgentApiConstants.MESSAGE_ARCHIVED_TRACE_NOT_FOUND);
        }
        AgentRuntimeIsolationService.RuntimeIsolationDecision isolationDecision =
                agentRuntimeIsolationService.acquire(AgentApiConstants.AGENT_TYPE_MULTI);
        if (!isolationDecision.allowed()) {
            return Result.fail(AgentApiConstants.HTTP_TOO_MANY_REQUESTS, isolationDecision.reasonMessage());
        }
        try {
            return Result.ok(multiAgentTraceService.replayArchivedTrace(
                    context.getUserId(),
                    archivedTrace.getTraceId(),
                    archivedTrace.getRequestSummary(),
                    new MultiAgentExecutionListener() { }));
        } finally {
            agentRuntimeIsolationService.release(AgentApiConstants.AGENT_TYPE_MULTI);
        }
    }

    @PostMapping("/logs/lifecycle/{agentType}/cleanup")
    public Result<AgentLogCleanupResponse> cleanupAgentLogs(
            @PathVariable(name = "agentType") String agentType,
            @RequestBody(required = false) AgentLogCleanupRequest body,
            HttpServletRequest request) {
        String deny = controllerSupport.checkAgentAccess(agentType, request, accessChecker, "WRITE");
        if (deny != null) {
            return Result.fail(AgentApiConstants.HTTP_FORBIDDEN, deny);
        }
        boolean dryRun = body == null || body.isDryRun();
        return Result.ok(agentLogLifecycleService.cleanup(agentType, dryRun));
    }

    @GetMapping("/multi/traces/{traceId}")
    public Result<MultiAgentTraceResponse> getMultiAgentTrace(
            @PathVariable(name = "traceId") String traceId,
            HttpServletRequest request) {
        String userId = controllerSupport.currentUserId(request);
        MultiAgentTraceResponse trace = multiAgentTraceService.getTrace(userId, traceId);
        if (trace == null) {
            return Result.fail(404, AgentApiConstants.MESSAGE_MULTI_TRACE_NOT_FOUND);
        }
        return Result.ok(trace);
    }

    @PostMapping("/multi/traces/{traceId}/recover")
    public Result<MultiAgentTraceResponse> recoverMultiAgentTrace(
            @PathVariable(name = "traceId") String traceId,
            @RequestBody MultiAgentTraceRecoverRequest body,
            HttpServletRequest request) {
        String userId = controllerSupport.currentUserId(request);
        AgentRuntimeIsolationService.RuntimeIsolationDecision isolationDecision =
                agentRuntimeIsolationService.acquire(AgentApiConstants.AGENT_TYPE_MULTI);
        if (!isolationDecision.allowed()) {
            return Result.fail(AgentApiConstants.HTTP_TOO_MANY_REQUESTS, isolationDecision.reasonMessage());
        }
        try {
            return Result.ok(multiAgentTraceService.recoverTrace(
                    userId,
                    traceId,
                    body != null ? body.getStepOrder() : null,
                    body != null ? body.getAction() : null,
                    new MultiAgentExecutionListener() { }));
        } finally {
            agentRuntimeIsolationService.release(AgentApiConstants.AGENT_TYPE_MULTI);
        }
    }

    @GetMapping("/multi/traces")
    public Result<List<MultiAgentTraceResponse>> listMultiAgentTraces(
            @RequestParam(name = "sessionId", required = false) String sessionId,
            @RequestParam(name = "limit", defaultValue = "20") Integer limit,
            HttpServletRequest request) {
        String userId = controllerSupport.currentUserId(request);
        int safeLimit = controllerSupport.safeLimit(limit, 20, 1, 100);
        return Result.ok(multiAgentTraceService.listTraces(userId, sessionId, safeLimit));
    }

}
