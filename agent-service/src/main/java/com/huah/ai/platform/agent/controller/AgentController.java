package com.huah.ai.platform.agent.controller;

import com.huah.ai.platform.agent.audit.AiAuditLog;
import com.huah.ai.platform.agent.audit.AiAuditLogMapper;
import com.huah.ai.platform.agent.audit.AiToolAuditLog;
import com.huah.ai.platform.agent.audit.AiToolAuditLogMapper;
import com.huah.ai.platform.agent.audit.ResponseFeedbackService;
import com.huah.ai.platform.agent.audit.TracePhaseRecord;
import com.huah.ai.platform.agent.dto.AgentChatRequest;
import com.huah.ai.platform.agent.dto.AgentChatResponse;
import com.huah.ai.platform.agent.dto.AgentAccessOverviewResponse;
import com.huah.ai.platform.agent.dto.AgentDiagnosticsResponse;
import com.huah.ai.platform.agent.dto.AgentArchivedTraceLookupResponse;
import com.huah.ai.platform.agent.dto.AgentLogArchiveDetailResponse;
import com.huah.ai.platform.agent.dto.AgentLogArchivePreviewResponse;
import com.huah.ai.platform.agent.dto.AgentLogCleanupRequest;
import com.huah.ai.platform.agent.dto.AgentLogCleanupResponse;
import com.huah.ai.platform.agent.dto.AgentLogLifecycleSummaryResponse;
import com.huah.ai.platform.agent.dto.AgentMetadataResponse;
import com.huah.ai.platform.agent.dto.AgentWorkbenchCompareResponse;
import com.huah.ai.platform.agent.dto.AgentWorkbenchSummaryResponse;
import com.huah.ai.platform.agent.dto.McpServerListResponse;
import com.huah.ai.platform.agent.dto.MultiAgentTaskRequest;
import com.huah.ai.platform.agent.dto.MultiAgentTraceRecoverRequest;
import com.huah.ai.platform.agent.dto.MultiAgentTraceResponse;
import com.huah.ai.platform.agent.dto.ResponseFeedbackRequest;
import com.huah.ai.platform.agent.dto.SessionTitleRequest;
import com.huah.ai.platform.agent.dto.SessionConfigRequest;
import com.huah.ai.platform.agent.dto.SessionConfigResponse;
import com.huah.ai.platform.agent.dto.SessionToggleRequest;
import com.huah.ai.platform.agent.dto.ToolSecurityOverviewResponse;
import com.huah.ai.platform.agent.memory.ConversationMemoryService;
import com.huah.ai.platform.agent.metrics.AiMetricsCollector;
import com.huah.ai.platform.agent.multi.MultiAgentExecutionListener;
import com.huah.ai.platform.agent.multi.MultiAgentExecutionResult;
import com.huah.ai.platform.agent.multi.MultiAgentExecutionStep;
import com.huah.ai.platform.agent.multi.MultiAgentExecutionTrace;
import com.huah.ai.platform.agent.multi.MultiAgentTraceService;
import com.huah.ai.platform.agent.security.AgentAccessChecker;
import com.huah.ai.platform.agent.security.ToolAccessDeniedException;
import com.huah.ai.platform.agent.security.ToolSecurityService;
import com.huah.ai.platform.agent.service.AgentChatResult;
import com.huah.ai.platform.agent.service.AgentAccessOverviewService;
import com.huah.ai.platform.agent.service.AgentLogArchiveService;
import com.huah.ai.platform.agent.service.AgentLogLifecycleService;
import com.huah.ai.platform.agent.service.AgentMetadataService;
import com.huah.ai.platform.agent.service.AgentRuntimeIsolationService;
import com.huah.ai.platform.agent.service.AgentWorkbenchService;
import com.huah.ai.platform.agent.service.AgentExecutionMetrics;
import com.huah.ai.platform.agent.service.AgentExecutionMetricsContext;
import com.huah.ai.platform.agent.service.AssistantAgent;
import com.huah.ai.platform.agent.service.AssistantAgentRegistry;
import com.huah.ai.platform.agent.service.McpServerCatalogService;
import com.huah.ai.platform.agent.tools.InternalApiTools;
import com.huah.ai.platform.common.dto.Result;
import com.huah.ai.platform.common.trace.TraceIdContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;

@Slf4j
@RestController
@RequestMapping("/api/v1/agent")
@RequiredArgsConstructor
public class AgentController {

    private static final String DEFAULT_SESSION_ID = "default";
    private static final String HEADER_SESSION_ID = "X-Session-Id";
    private static final String AGENT_TYPE_MULTI = "multi";
    private static final int PRE_DEDUCT_TOKENS = 500;
    private static final int HTTP_BAD_REQUEST = 400;
    private static final int HTTP_FORBIDDEN = 403;
    private static final int HTTP_TOO_MANY_REQUESTS = 429;
    private static final int HTTP_INTERNAL_SERVER_ERROR = 500;
    private static final long EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS = 5L;
    private static final String MESSAGE_MESSAGE_REQUIRED = "message must not be blank";
    private static final String MESSAGE_TASK_REQUIRED = "task must not be blank";
    private static final String MESSAGE_TITLE_REQUIRED = "title must not be blank";
    private static final String MESSAGE_AI_UNAVAILABLE = "AI service is temporarily unavailable. Please retry later.";
    private static final String MESSAGE_STREAM_PERMISSION_DENIED = "[permission denied] ";
    private static final String MESSAGE_STREAM_QUOTA_DENIED = "[quota exceeded] ";
    private static final String MESSAGE_STREAM_RUNTIME_ISOLATION = "[runtime isolation] ";
    private static final String MESSAGE_STREAM_STAGE_RUNNING = " running...";
    private static final String MESSAGE_STREAM_AI_UNAVAILABLE = "\n\n[AI service is temporarily unavailable. Please retry later.]";
    private static final String MESSAGE_SESSION_ACCESS_DENIED = "You do not have permission to access this session";
    private static final String MESSAGE_SESSION_CONFIG_ACCESS_DENIED = "You do not have permission to access this session configuration";
    private static final String MESSAGE_SESSION_CONFIG_UPDATED = "Session configuration updated";
    private static final String MESSAGE_SESSION_TITLE_ACCESS_DENIED = "You do not have permission to update this session";
    private static final String MESSAGE_MULTI_TRACE_NOT_FOUND = "Multi-agent execution trace not found";
    private static final String PHASE_AUTH_LABEL = "Auth and context";
    private static final String PHASE_PREPARATION_LABEL = "Request preparation";
    private static final String PHASE_TOOLS_LABEL = "Tool execution";
    private static final String PHASE_GENERATION_LABEL = "Model generation";
    private static final String PHASE_PERSISTENCE_LABEL = "Persistence and audit";
    private static final String PHASE_AUTH_DESCRIPTION = "Request entry, authorization checks, and quota validation.";
    private static final String PHASE_PREPARATION_DESCRIPTION = "Session configuration loading, prompt assembly, and model request setup.";
    private static final String PHASE_TOOLS_DESCRIPTION = "Measured time spent in tool execution based on audit records.";
    private static final String PHASE_GENERATION_DESCRIPTION = "Model inference and response generation.";
    private static final String PHASE_PERSISTENCE_DESCRIPTION = "Audit log persistence and response finalization.";

    private final AssistantAgentRegistry assistantAgentRegistry;
    private final MultiAgentTraceService multiAgentTraceService;
    private final ConversationMemoryService memoryService;
    private final AiAuditLogMapper auditLogMapper;
    private final AiToolAuditLogMapper toolAuditLogMapper;
    private final ResponseFeedbackService feedbackService;
    private final AgentAccessChecker accessChecker;
    private final ToolSecurityService toolSecurityService;
    private final AiMetricsCollector metricsCollector;
    private final AgentRequestContextResolver requestContextResolver;
    private final McpServerCatalogService mcpServerCatalogService;
    private final AgentAccessOverviewService agentAccessOverviewService;
    private final AgentWorkbenchService agentWorkbenchService;
    private final AgentLogLifecycleService agentLogLifecycleService;
    private final AgentLogArchiveService agentLogArchiveService;
    private final AgentMetadataService agentMetadataService;
    private final AgentRuntimeIsolationService agentRuntimeIsolationService;
    private final InternalApiTools internalApiTools;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor = Executors.newCachedThreadPool(new AgentControllerThreadFactory());

    @PreDestroy
    void shutdownExecutor() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    @PostMapping("/{agentType}/chat")
    public Result<AgentChatResponse> chat(
            @PathVariable(name = "agentType") String agentType,
            @RequestBody AgentChatRequest body,
            @RequestHeader(value = HEADER_SESSION_ID, defaultValue = DEFAULT_SESSION_ID) String sessionId,
            HttpServletRequest request) {
        AgentRequestContext context = requestContextResolver.resolve(request);
        String userId = context.getUserId();
        String roles = context.getRoles();
        String department = context.getDepartment();

        String deny = accessChecker.checkPermission(agentType, roles, department);
        if (deny != null) {
            log.warn("[Chat] permission denied agent={}, userId={}, roles={}, reason={}", agentType, userId, roles, deny);
            return Result.fail(HTTP_FORBIDDEN, deny);
        }

        String quotaDeny = accessChecker.checkAndConsumeTokens(userId, agentType, PRE_DEDUCT_TOKENS);
        if (quotaDeny != null) {
            log.warn("[Chat] token quota exceeded agent={}, userId={}", agentType, userId);
            return Result.fail(HTTP_TOO_MANY_REQUESTS, quotaDeny);
        }
        AgentRuntimeIsolationService.RuntimeIsolationDecision isolationDecision = agentRuntimeIsolationService.acquire(agentType);
        if (!isolationDecision.allowed()) {
            return Result.fail(HTTP_TOO_MANY_REQUESTS, isolationDecision.reasonMessage());
        }

        String message = body.getMessage();
        if (message == null || message.isBlank()) {
            return Result.fail(HTTP_BAD_REQUEST, MESSAGE_MESSAGE_REQUIRED);
        }
        if (body.getSessionConfig() != null) {
            memoryService.saveSessionConfig(sessionId, body.getSessionConfig());
        }

        log.info("[Chat] received agent={}, userId={}, sessionId={}, messageLength={}",
                agentType, userId, sessionId, message.length());
        log.info("[Chat] input agent={}, userId={}, message={}",
                agentType, userId, truncate(message, 500));

        long startTime = System.currentTimeMillis();
        try {
            long agentStartTime = System.currentTimeMillis();
            AgentChatResult result;
            String traceId = null;
            if (AGENT_TYPE_MULTI.equals(agentType)) {
                MultiAgentExecutionResult multiResult = multiAgentTraceService.execute(userId, sessionId, message, new MultiAgentExecutionListener() {
                });
                result = new AgentChatResult(multiResult.getContent(), multiResult.getPromptTokens(), multiResult.getCompletionTokens());
                traceId = multiResult.getTraceId();
            } else {
                result = routeToAgent(agentType, userId, sessionId, message);
            }
            long latency = System.currentTimeMillis() - startTime;
            long persistenceStartTime = System.currentTimeMillis();
            AgentExecutionMetrics executionMetrics = result.getExecutionMetrics();
            String responseId = saveAuditLog(userId, sessionId, agentType, message, result.getContent(), latency, true, null,
                    Math.max(0, agentStartTime - startTime),
                    executionMetrics != null ? executionMetrics.getPreparationLatencyMs() : 0L,
                    executionMetrics != null ? executionMetrics.getModelLatencyMs() : Math.max(0, System.currentTimeMillis() - agentStartTime),
                    Math.max(0, System.currentTimeMillis() - persistenceStartTime),
                    result.getPromptTokens(), result.getCompletionTokens());
            metricsCollector.recordModelCall(agentType, latency, true);
            metricsCollector.recordRequest(null, agentType, latency, true,
                    result.getPromptTokens(), result.getCompletionTokens());
            log.info("[Chat] output agent={}, userId={}, latency={}ms, responseLength={}, promptTokens={}, completionTokens={}, response={}",
                    agentType, userId, latency,
                    result.getContent() != null ? result.getContent().length() : 0,
                    result.getPromptTokens(), result.getCompletionTokens(),
                    truncate(result.getContent(), 500));
            accessChecker.recordActualTokens(userId,
                    result.getPromptTokens() + result.getCompletionTokens(),
                    PRE_DEDUCT_TOKENS);
            return Result.ok(AgentChatResponse.builder()
                    .responseId(responseId)
                    .content(result.getContent())
                    .traceId(traceId)
                    .build());
        } catch (ToolAccessDeniedException e) {
            long latency = System.currentTimeMillis() - startTime;
            metricsCollector.recordModelCall(agentType, latency, false);
            metricsCollector.recordRequest(null, agentType, latency, false, 0, 0);
            accessChecker.recordActualTokens(userId, 0, PRE_DEDUCT_TOKENS);
            memoryService.rollbackLastUserMessage(sessionId);
            throw e;
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            metricsCollector.recordModelCall(AGENT_TYPE_MULTI, latency, false);
            metricsCollector.recordRequest(null, AGENT_TYPE_MULTI, latency, false, 0, 0);
            metricsCollector.recordModelCall(agentType, latency, false);
            metricsCollector.recordRequest(null, agentType, latency, false, 0, 0);
            log.error("[Chat] failed agent={}, userId={}, latency={}ms, error={}",
                    agentType, userId, latency, e.getMessage(), e);
            accessChecker.recordActualTokens(userId, 0, PRE_DEDUCT_TOKENS);
            memoryService.rollbackLastUserMessage(sessionId);
            return Result.fail(HTTP_INTERNAL_SERVER_ERROR, MESSAGE_AI_UNAVAILABLE);
        } finally {
            agentRuntimeIsolationService.release(agentType);
        }
    }

    @PostMapping(value = "/{agentType}/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(
            @PathVariable(name = "agentType") String agentType,
            @RequestBody AgentChatRequest body,
            @RequestHeader(value = HEADER_SESSION_ID, defaultValue = DEFAULT_SESSION_ID) String sessionId,
            HttpServletRequest request) {

        long streamTimeoutMs = agentRuntimeIsolationService.getStreamTimeoutMs(agentType, 180_000L);
        SseEmitter emitter = new SseEmitter(streamTimeoutMs);
        AgentRequestContext context = requestContextResolver.resolve(request);
        String userId = context.getUserId();
        String roles = context.getRoles();
        String department = context.getDepartment();

        String deny = accessChecker.checkPermission(agentType, roles, department);
        if (deny != null) {
            log.warn("[Stream] permission denied agent={}, userId={}, roles={}, reason={}", agentType, userId, roles, deny);
            sendChunk(emitter, "[权限不足] " + deny);
            sendDone(emitter, null);
            return emitter;
        }

        String quotaDeny = accessChecker.checkAndConsumeTokens(userId, agentType, PRE_DEDUCT_TOKENS);
        if (quotaDeny != null) {
            log.warn("[Stream] token quota exceeded agent={}, userId={}", agentType, userId);
            sendChunk(emitter, "[配额不足] " + quotaDeny);
            sendDone(emitter, null);
            return emitter;
        }

        String message = body.getMessage();
        if (message == null || message.isBlank()) {
            log.warn("[Stream] empty message, agent={}, userId={}", agentType, userId);
            sendChunk(emitter, "message 不能为空");
            sendDone(emitter, null);
            return emitter;
        }
        if (body.getSessionConfig() != null) {
            memoryService.saveSessionConfig(sessionId, body.getSessionConfig());
        }

        AgentRuntimeIsolationService.RuntimeIsolationDecision streamIsolationDecision = agentRuntimeIsolationService.acquire(agentType);
        if (!streamIsolationDecision.allowed()) {
            sendChunk(emitter, MESSAGE_STREAM_RUNTIME_ISOLATION + streamIsolationDecision.reasonMessage());
            sendDone(emitter, null);
            return emitter;
        }

        log.info("[Stream] received agent={}, userId={}, sessionId={}, messageLength={}",
                agentType, userId, sessionId, message.length());
        log.info("[Stream] input agent={}, userId={}, message={}",
                agentType, userId, truncate(message, 500));

        long startTime = System.currentTimeMillis();
        AtomicInteger chunkCount = new AtomicInteger(0);
        StringBuilder fullResponse = new StringBuilder();
        AtomicInteger totalPromptTokens = new AtomicInteger(0);
        AtomicInteger totalCompletionTokens = new AtomicInteger(0);

        metricsCollector.incrementActive();

        executor.submit(() -> {
            try {
            if (AGENT_TYPE_MULTI.equals(agentType)) {
                    log.info("[Stream] multi-agent started userId={}", userId);
                    MultiAgentExecutionResult multiResult = multiAgentTraceService.execute(userId, sessionId, message, new MultiAgentExecutionListener() {
                        @Override
                        public void onStageStarted(String stage, String label) {
                            sendChunk(emitter, "**[" + label + "] 正在执行...**\n\n");
                        }

                        @Override
                        public void onStageCompleted(MultiAgentExecutionStep step) {
                            if (Boolean.TRUE.equals(step.getSuccess())) {
                                String output = step.getOutputSummary() == null ? "" : step.getOutputSummary();
                                String suffix = "critic".equals(step.getStage()) ? "" : "\n\n---\n\n";
                                sendChunk(emitter, output + suffix);
                            }
                        }

                        @Override
                        public void onFailed(String stage, String errorMessage) {
                            log.warn("[Stream] multi-agent stage failed userId={}, stage={}, error={}", userId, stage, errorMessage);
                        }
                    });

                    

                    metricsCollector.decrementActive();
                    long latency = System.currentTimeMillis() - startTime;
                    metricsCollector.recordModelCall(AGENT_TYPE_MULTI, latency, true);
                    metricsCollector.recordRequest(null, AGENT_TYPE_MULTI, latency, true,
                            multiResult.getPromptTokens(), multiResult.getCompletionTokens());
                    log.info("[Stream] multi-agent completed userId={}, latency={}ms, tokens={}/{}",
                            userId, latency, multiResult.getPromptTokens(), multiResult.getCompletionTokens());
                    String responseId = saveAuditLog(userId, sessionId, AGENT_TYPE_MULTI, message, truncate(multiResult.getContent(), 500),
                            latency, true, null, 0L, 0L, latency, 0L,
                            multiResult.getPromptTokens(), multiResult.getCompletionTokens());
                    accessChecker.recordActualTokens(userId,
                            multiResult.getPromptTokens() + multiResult.getCompletionTokens(),
                            PRE_DEDUCT_TOKENS);
                    sendDone(emitter, responseId, multiResult.getTraceId());
                    return;
                }

                long agentStartTime = System.currentTimeMillis();
                Flux<ChatResponse> flux = routeToAgentStream(agentType, userId, sessionId, message);
                AgentExecutionMetrics executionMetrics = AgentExecutionMetricsContext.getAndClear();
                flux.doOnNext(chatResponse -> {
                            chunkCount.incrementAndGet();
                            String chunk = "";
                            if (chatResponse.getResult() != null && chatResponse.getResult().getOutput() != null) {
                                chunk = chatResponse.getResult().getOutput().getText();
                                if (chunk == null) {
                                    chunk = "";
                                }
                            }
                            fullResponse.append(chunk);
                            sendChunk(emitter, chunk);

                            if (chatResponse.getMetadata() != null && chatResponse.getMetadata().getUsage() != null) {
                                var usage = chatResponse.getMetadata().getUsage();
                                if (usage.getPromptTokens() > 0) {
                                    totalPromptTokens.set((int) usage.getPromptTokens());
                                }
                                if (usage.getCompletionTokens() > 0) {
                                    totalCompletionTokens.set((int) usage.getCompletionTokens());
                                }
                            }
                        })
                        .doOnComplete(() -> {
                            metricsCollector.decrementActive();
                            long latency = System.currentTimeMillis() - startTime;
                            long persistenceStartTime = System.currentTimeMillis();
                            metricsCollector.recordModelCall(agentType, latency, true);
                            metricsCollector.recordRequest(null, agentType, latency, true,
                                    totalPromptTokens.get(), totalCompletionTokens.get());
                            String response = fullResponse.toString();
                            log.info("[Stream] output agent={}, userId={}, latency={}ms, chunks={}, responseLength={}, promptTokens={}, completionTokens={}, response={}",
                                    agentType, userId, latency, chunkCount.get(),
                                    response.length(), totalPromptTokens.get(), totalCompletionTokens.get(),
                                    truncate(response, 500));
                            accessChecker.recordActualTokens(userId,
                                    totalPromptTokens.get() + totalCompletionTokens.get(),
                                    PRE_DEDUCT_TOKENS);
                            String responseId = saveAuditLog(userId, sessionId, agentType, message, response, latency, true, null,
                                    Math.max(0, agentStartTime - startTime),
                                    executionMetrics != null ? executionMetrics.getPreparationLatencyMs() : 0L,
                                    Math.max(0, System.currentTimeMillis() - agentStartTime),
                                    Math.max(0, System.currentTimeMillis() - persistenceStartTime),
                                    totalPromptTokens.get(), totalCompletionTokens.get());
                            sendDone(emitter, responseId);
                        })
                        .doOnError(e -> {
                            metricsCollector.decrementActive();
                            long latency = System.currentTimeMillis() - startTime;
                            long persistenceStartTime = System.currentTimeMillis();
                            metricsCollector.recordModelCall(agentType, latency, false);
                            metricsCollector.recordRequest(null, agentType, latency, false, 0, 0);
                            log.error("[Stream] failed agent={}, userId={}, latency={}ms, chunks={}, partialResponse={}, error={}",
                                    agentType, userId, latency, chunkCount.get(),
                                    truncate(fullResponse.toString(), 200), e.getMessage(), e);
                            accessChecker.recordActualTokens(userId, 0, PRE_DEDUCT_TOKENS);
                            saveAuditLog(userId, sessionId, agentType, message, null, latency, false, e.getMessage(),
                                    Math.max(0, agentStartTime - startTime),
                                    executionMetrics != null ? executionMetrics.getPreparationLatencyMs() : 0L,
                                    Math.max(0, System.currentTimeMillis() - agentStartTime),
                                    Math.max(0, System.currentTimeMillis() - persistenceStartTime),
                                    0, 0);
                            memoryService.rollbackLastUserMessage(sessionId);
                            sendChunk(emitter, "\n\n[AI 服务异常，请稍后重试]");
                            sendDone(emitter, null);
                        })
                        .subscribe();
            } catch (Exception e) {
                metricsCollector.decrementActive();
                long latency = System.currentTimeMillis() - startTime;
                metricsCollector.recordModelCall(agentType, latency, false);
                metricsCollector.recordRequest(null, agentType, latency, false, 0, 0);
                log.error("[Stream] execution failed agent={}, userId={}, latency={}ms, error={}",
                        agentType, userId, latency, e.getMessage(), e);
                accessChecker.recordActualTokens(userId, 0, PRE_DEDUCT_TOKENS);
                if (!AGENT_TYPE_MULTI.equals(agentType)) {
                    memoryService.rollbackLastUserMessage(sessionId);
                }
                sendChunk(emitter, "\n\n[AI 服务异常，请稍后重试]");
                sendDone(emitter, null);
            }
        });

        emitter.onTimeout(() -> {
            long latency = System.currentTimeMillis() - startTime;
            log.warn("[Stream] timeout agent={}, userId={}, latency={}ms, chunks={}",
                    agentType, userId, latency, chunkCount.get());
            agentRuntimeIsolationService.release(agentType);
        });

        emitter.onCompletion(() -> agentRuntimeIsolationService.release(agentType));

        return emitter;
    }

    @PostMapping("/multi/execute")
    public Result<String> multiAgentExecute(
            @RequestBody MultiAgentTaskRequest body,
            @RequestHeader(value = HEADER_SESSION_ID, defaultValue = DEFAULT_SESSION_ID) String sessionId,
            HttpServletRequest request) {
        AgentRequestContext context = requestContextResolver.resolve(request);
        String userId = context.getUserId();
        String roles = context.getRoles();
        String department = context.getDepartment();

        String deny = accessChecker.checkPermission(AGENT_TYPE_MULTI, roles, department);
        if (deny != null) {
            return Result.fail(HTTP_FORBIDDEN, deny);
        }

        String task = body.getTask();
        if (task == null || task.isBlank()) {
            return Result.fail(HTTP_BAD_REQUEST, MESSAGE_TASK_REQUIRED);
        }

        log.info("[Multi] received userId={}, taskLength={}", userId, task.length());
        log.info("[Multi] input userId={}, task={}", userId, truncate(task, 500));

        long startTime = System.currentTimeMillis();
        try {
            var result = multiAgentTraceService.execute(userId, sessionId, task, new MultiAgentExecutionListener() {
            });
            long latency = System.currentTimeMillis() - startTime;
            metricsCollector.recordModelCall(AGENT_TYPE_MULTI, latency, true);
            metricsCollector.recordRequest(null, AGENT_TYPE_MULTI, latency, true,
                    result.getPromptTokens(), result.getCompletionTokens());
            log.info("[Multi] output userId={}, latency={}ms, responseLength={}, tokens={}/{}, response={}",
                    userId, latency,
                    result.getContent() != null ? result.getContent().length() : 0,
                    result.getPromptTokens(), result.getCompletionTokens(),
                    truncate(result.getContent(), 500));
            return Result.ok(result.getContent());
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            log.error("[Multi] failed userId={}, latency={}ms, error={}",
                    userId, latency, e.getMessage(), e);
            throw e;
        }
    }

    @DeleteMapping("/{agentType}/memory")
    public Result<String> clearMemory(
            @PathVariable(name = "agentType") String agentType,
            @RequestHeader(value = HEADER_SESSION_ID, defaultValue = DEFAULT_SESSION_ID) String sessionId,
            HttpServletRequest request) {
        String userId = requestContextResolver.resolve(request).getUserId();
        if (!ownsSession(userId, agentType, sessionId)) {
            return Result.fail(HTTP_FORBIDDEN, MESSAGE_SESSION_ACCESS_DENIED);
        }
        log.info("[Memory] clear agent={}, userId={}, sessionId={}", agentType, userId, sessionId);
        memoryService.clearMemory(sessionId);
        return Result.ok("会话记忆已清除");
    }

    @GetMapping("/{agentType}/memory")
    public Result<List<Map<String, String>>> getHistory(
            @PathVariable(name = "agentType") String agentType,
            @RequestHeader(value = HEADER_SESSION_ID, defaultValue = DEFAULT_SESSION_ID) String sessionId,
            HttpServletRequest request) {
        String userId = requestContextResolver.resolve(request).getUserId();
        if (!ownsSession(userId, agentType, sessionId)) {
            return Result.fail(HTTP_FORBIDDEN, MESSAGE_SESSION_ACCESS_DENIED);
        }
        log.info("[Memory] history agent={}, userId={}, sessionId={}", agentType, userId, sessionId);
        return Result.ok(memoryService.getHistory(sessionId));
    }

    @GetMapping("/{agentType}/sessions")
    public Result<List<Map<String, String>>> listSessions(
            @PathVariable(name = "agentType") String agentType,
            @RequestHeader(value = HEADER_SESSION_ID, required = false) String ignoredSessionId,
            @org.springframework.web.bind.annotation.RequestParam(name = "keyword", required = false) String keyword,
            @org.springframework.web.bind.annotation.RequestParam(name = "includeArchived", defaultValue = "false") boolean includeArchived,
            @org.springframework.web.bind.annotation.RequestParam(name = "pinnedOnly", defaultValue = "false") boolean pinnedOnly,
            @org.springframework.web.bind.annotation.RequestParam(name = "updatedAfter", required = false) Long updatedAfter,
            @org.springframework.web.bind.annotation.RequestParam(name = "updatedBefore", required = false) Long updatedBefore,
            @org.springframework.web.bind.annotation.RequestParam(name = "limit", required = false) Integer limit,
            HttpServletRequest request) {
        String userId = requestContextResolver.resolve(request).getUserId();
        String prefix = buildSessionPrefix(userId, agentType);
        log.info("[Sessions] list agent={}, userId={}, prefix={}", agentType, userId, prefix);
        return Result.ok(memoryService.searchSessions(prefix, keyword, includeArchived, pinnedOnly, updatedAfter, updatedBefore, limit));
    }

    @GetMapping("/{agentType}/sessions/config")
    public Result<SessionConfigResponse> getSessionConfig(
            @PathVariable(name = "agentType") String agentType,
            @RequestHeader(value = HEADER_SESSION_ID) String sessionId,
            HttpServletRequest request) {
        String userId = requestContextResolver.resolve(request).getUserId();
        if (!ownsSession(userId, agentType, sessionId)) {
            return Result.fail(HTTP_FORBIDDEN, MESSAGE_SESSION_CONFIG_ACCESS_DENIED);
        }
        return Result.ok(memoryService.getSessionConfig(sessionId));
    }

    @PostMapping("/{agentType}/sessions/config")
    public Result<String> saveSessionConfig(
            @PathVariable(name = "agentType") String agentType,
            @RequestHeader(value = HEADER_SESSION_ID) String sessionId,
            @RequestBody SessionConfigRequest body,
            HttpServletRequest request) {
        String userId = requestContextResolver.resolve(request).getUserId();
        if (!ownsSession(userId, agentType, sessionId)) {
            return Result.fail(HTTP_FORBIDDEN, MESSAGE_SESSION_CONFIG_ACCESS_DENIED);
        }
        memoryService.saveSessionConfig(sessionId, body);
            return Result.ok(MESSAGE_SESSION_CONFIG_UPDATED);
    }

    @PostMapping("/{agentType}/sessions/title")
    public Result<String> renameSessionTitle(
            @PathVariable(name = "agentType") String agentType,
            @RequestHeader(value = HEADER_SESSION_ID) String sessionId,
            @RequestBody SessionTitleRequest body,
            HttpServletRequest request) {
        String userId = requestContextResolver.resolve(request).getUserId();
        if (!ownsSession(userId, agentType, sessionId)) {
            return Result.fail(HTTP_FORBIDDEN, MESSAGE_SESSION_TITLE_ACCESS_DENIED);
        }

        String title = body.getTitle();
        if (title == null || title.isBlank()) {
            return Result.fail(HTTP_BAD_REQUEST, MESSAGE_TITLE_REQUIRED);
        }

        memoryService.renameSession(sessionId, title);
        return Result.ok("会话标题已更新");
    }

    @PostMapping("/{agentType}/sessions/pin")
    public Result<String> pinSession(
            @PathVariable(name = "agentType") String agentType,
            @RequestHeader(value = HEADER_SESSION_ID) String sessionId,
            @RequestBody SessionToggleRequest body,
            HttpServletRequest request) {
        String userId = requestContextResolver.resolve(request).getUserId();
        if (!ownsSession(userId, agentType, sessionId)) {
            return Result.fail(HTTP_FORBIDDEN, MESSAGE_SESSION_TITLE_ACCESS_DENIED);
        }

        boolean pinned = Boolean.TRUE.equals(body.getPinned());
        memoryService.pinSession(sessionId, pinned);
        return Result.ok(pinned ? "会话已置顶" : "会话已取消置顶");
    }

    @PostMapping("/{agentType}/sessions/archive")
    public Result<String> archiveSession(
            @PathVariable(name = "agentType") String agentType,
            @RequestHeader(value = HEADER_SESSION_ID) String sessionId,
            @RequestBody SessionToggleRequest body,
            HttpServletRequest request) {
        String userId = requestContextResolver.resolve(request).getUserId();
        if (!ownsSession(userId, agentType, sessionId)) {
            return Result.fail(HTTP_FORBIDDEN, MESSAGE_SESSION_TITLE_ACCESS_DENIED);
        }

        boolean archived = Boolean.TRUE.equals(body.getArchived());
        memoryService.archiveSession(sessionId, archived);
        return Result.ok(archived ? "会话已归档" : "会话已取消归档");
    }

    @DeleteMapping("/{agentType}/sessions")
    public Result<String> deleteSession(
            @PathVariable(name = "agentType") String agentType,
            @RequestHeader(value = HEADER_SESSION_ID) String sessionId,
            HttpServletRequest request) {
        String userId = requestContextResolver.resolve(request).getUserId();
        if (!ownsSession(userId, agentType, sessionId)) {
            return Result.fail(HTTP_FORBIDDEN, MESSAGE_SESSION_ACCESS_DENIED);
        }

        memoryService.clearMemory(sessionId);
        return Result.ok("会话已删除");
    }

    @PostMapping("/feedback")
    public Result<String> submitFeedback(@RequestBody ResponseFeedbackRequest body, HttpServletRequest request) {
        String userId = requestContextResolver.resolve(request).getUserId();
        feedbackService.submitAgentFeedback(userId, body.getResponseId(), body.getFeedback(), body.getComment());
        return Result.ok("反馈已提交");
    }

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
        AgentRequestContext context = requestContextResolver.resolve(request);
        String deny = accessChecker.checkPermission(agentType, context.getRoles(), context.getDepartment());
        if (deny != null) {
            return Result.fail(HTTP_FORBIDDEN, deny);
        }
        return Result.ok(mcpServerCatalogService.listServers(agentType));
    }

    @GetMapping("/tools/audit")
    public Result<List<AiToolAuditLog>> listToolAuditLogs(
            @org.springframework.web.bind.annotation.RequestParam(name = "agentType", required = false) String agentType,
            @org.springframework.web.bind.annotation.RequestParam(name = "toolName", required = false) String toolName,
            @org.springframework.web.bind.annotation.RequestParam(name = "traceId", required = false) String traceId,
            @org.springframework.web.bind.annotation.RequestParam(name = "limit", defaultValue = "50") Integer limit,
            HttpServletRequest request) {
        String userId = requestContextResolver.resolve(request).getUserId();
        int safeLimit = limit == null ? 50 : Math.max(1, Math.min(limit, 200));
        return Result.ok(toolAuditLogMapper.selectRecent(userId, agentType, toolName, traceId, safeLimit));
    }

    @GetMapping("/tools/security/{agentType}")
    public Result<ToolSecurityOverviewResponse> getToolSecurityOverview(
            @PathVariable(name = "agentType") String agentType,
            HttpServletRequest request) {
        AgentRequestContext context = requestContextResolver.resolve(request);
        String deny = accessChecker.checkPermission(agentType, context.getRoles(), context.getDepartment());
        if (deny != null) {
            return Result.fail(HTTP_FORBIDDEN, deny);
        }
        return Result.ok(ToolSecurityOverviewResponse.builder()
                .securityEnabled(toolSecurityService.isSecurityEnabled())
                .agentType(agentType)
                .allowedTools(toolSecurityService.getAllowedTools(agentType))
                .allowedConnectors(toolSecurityService.getAllowedConnectors(agentType))
                .enabledConnectors(internalApiTools.listEnabledConnectorCodes())
                .build());
    }

    @GetMapping("/diagnostics/{agentType}")
    public Result<AgentDiagnosticsResponse> getAgentDiagnostics(
            @PathVariable(name = "agentType") String agentType,
            HttpServletRequest request) {
        AgentRequestContext context = requestContextResolver.resolve(request);
        String deny = accessChecker.checkPermission(agentType, context.getRoles(), context.getDepartment());
        if (deny != null) {
            return Result.fail(HTTP_FORBIDDEN, deny);
        }
        McpServerListResponse mcpServers = mcpServerCatalogService.listServers(agentType);
        List<String> allowedTools = toolSecurityService.getAllowedTools(agentType);
        List<String> allowedConnectors = toolSecurityService.getAllowedConnectors(agentType);
        List<String> allowedMcpServers = toolSecurityService.getAllowedMcpServers(agentType);
        int recentMultiTraceCount = AGENT_TYPE_MULTI.equals(agentType)
                ? multiAgentTraceService.listTraces(context.getUserId(), null, 10).size()
                : 0;
        String summary = buildAgentDiagnosticSummary(agentType, allowedTools, allowedConnectors, allowedMcpServers,
                mcpServers.getCount(), mcpServers.getIssueCount(), recentMultiTraceCount);
        return Result.ok(AgentDiagnosticsResponse.builder()
                .agentType(agentType)
                .accessible(true)
                .toolSecurityEnabled(toolSecurityService.isSecurityEnabled())
                .allowedTools(allowedTools)
                .allowedConnectors(allowedConnectors)
                .allowedMcpServers(allowedMcpServers)
                .enabledConnectors(internalApiTools.listEnabledConnectorCodes())
                .recentMultiTraceCount(recentMultiTraceCount)
                .availableMcpServerCount(mcpServers.getCount())
                .mcpIssueCount(mcpServers.getIssueCount())
                .summary(summary)
                .build());
    }

    @GetMapping("/access/{agentType}")
    public Result<AgentAccessOverviewResponse> getAgentAccessOverview(
            @PathVariable(name = "agentType") String agentType,
            HttpServletRequest request) {
        AgentRequestContext context = requestContextResolver.resolve(request);
        String deny = accessChecker.checkPermission(agentType, context.getRoles(), context.getDepartment());
        if (deny != null) {
            return Result.fail(HTTP_FORBIDDEN, deny);
        }
        return Result.ok(agentAccessOverviewService.build(agentType));
    }

    @GetMapping("/workbench/{agentType}")
    public Result<AgentWorkbenchSummaryResponse> getAgentWorkbenchSummary(
            @PathVariable(name = "agentType") String agentType,
            HttpServletRequest request) {
        AgentRequestContext context = requestContextResolver.resolve(request);
        String deny = accessChecker.checkPermission(agentType, context.getRoles(), context.getDepartment());
        if (deny != null) {
            return Result.fail(HTTP_FORBIDDEN, deny);
        }
        return Result.ok(agentWorkbenchService.build(agentType));
    }

    @GetMapping("/workbench/compare")
    public Result<AgentWorkbenchCompareResponse> compareAgentWorkbench(
            @org.springframework.web.bind.annotation.RequestParam(name = "leftAgent") String leftAgent,
            @org.springframework.web.bind.annotation.RequestParam(name = "rightAgent") String rightAgent,
            HttpServletRequest request) {
        AgentRequestContext context = requestContextResolver.resolve(request);
        String leftDeny = accessChecker.checkPermission(leftAgent, context.getRoles(), context.getDepartment());
        if (leftDeny != null) {
            return Result.fail(HTTP_FORBIDDEN, leftDeny);
        }
        String rightDeny = accessChecker.checkPermission(rightAgent, context.getRoles(), context.getDepartment());
        if (rightDeny != null) {
            return Result.fail(HTTP_FORBIDDEN, rightDeny);
        }
        return Result.ok(agentWorkbenchService.compare(leftAgent, rightAgent));
    }

    @GetMapping("/logs/lifecycle/{agentType}")
    public Result<AgentLogLifecycleSummaryResponse> getAgentLogLifecycleSummary(
            @PathVariable(name = "agentType") String agentType,
            HttpServletRequest request) {
        AgentRequestContext context = requestContextResolver.resolve(request);
        String deny = accessChecker.checkPermission(agentType, context.getRoles(), context.getDepartment());
        if (deny != null) {
            return Result.fail(HTTP_FORBIDDEN, deny);
        }
        return Result.ok(agentLogLifecycleService.buildSummary(agentType));
    }

    @GetMapping("/logs/lifecycle/{agentType}/archive/latest")
    public Result<AgentLogArchiveDetailResponse> getLatestAgentLogArchive(
            @PathVariable(name = "agentType") String agentType,
            HttpServletRequest request) {
        AgentRequestContext context = requestContextResolver.resolve(request);
        String deny = accessChecker.checkPermission(agentType, context.getRoles(), context.getDepartment());
        if (deny != null) {
            return Result.fail(HTTP_FORBIDDEN, deny);
        }
        return Result.ok(agentLogArchiveService.loadLatestManifest(agentType));
    }

    @GetMapping("/logs/lifecycle/{agentType}/archive/latest/preview")
    public Result<AgentLogArchivePreviewResponse> previewLatestAgentLogArchive(
            @PathVariable(name = "agentType") String agentType,
            @org.springframework.web.bind.annotation.RequestParam(name = "artifactType") String artifactType,
            @org.springframework.web.bind.annotation.RequestParam(name = "limit", defaultValue = "5") Integer limit,
            HttpServletRequest request) {
        AgentRequestContext context = requestContextResolver.resolve(request);
        String deny = accessChecker.checkPermission(agentType, context.getRoles(), context.getDepartment());
        if (deny != null) {
            return Result.fail(HTTP_FORBIDDEN, deny);
        }
        int safeLimit = limit == null ? 5 : Math.max(1, Math.min(limit, 20));
        return Result.ok(agentLogArchiveService.previewLatestArtifact(agentType, artifactType, safeLimit));
    }

    @GetMapping("/logs/lifecycle/{agentType}/archive/latest/trace")
    public Result<AgentArchivedTraceLookupResponse> findLatestArchivedTrace(
            @PathVariable(name = "agentType") String agentType,
            @org.springframework.web.bind.annotation.RequestParam(name = "traceId") String traceId,
            HttpServletRequest request) {
        AgentRequestContext context = requestContextResolver.resolve(request);
        String deny = accessChecker.checkPermission(agentType, context.getRoles(), context.getDepartment());
        if (deny != null) {
            return Result.fail(HTTP_FORBIDDEN, deny);
        }
        return Result.ok(agentLogArchiveService.findArchivedTrace(agentType, traceId));
    }

    @PostMapping("/logs/lifecycle/{agentType}/archive/latest/trace/replay")
    public Result<MultiAgentTraceResponse> replayLatestArchivedTrace(
            @PathVariable(name = "agentType") String agentType,
            @org.springframework.web.bind.annotation.RequestParam(name = "traceId") String traceId,
            HttpServletRequest request) {
        AgentRequestContext context = requestContextResolver.resolve(request);
        String deny = accessChecker.checkPermission(agentType, context.getRoles(), context.getDepartment());
        if (deny != null) {
            return Result.fail(HTTP_FORBIDDEN, deny);
        }
        MultiAgentExecutionTrace archivedTrace = agentLogArchiveService.loadArchivedTraceRecord(agentType, traceId);
        if (archivedTrace == null) {
            return Result.fail(404, "Archived trace not found");
        }
        AgentRuntimeIsolationService.RuntimeIsolationDecision isolationDecision = agentRuntimeIsolationService.acquire(AGENT_TYPE_MULTI);
        if (!isolationDecision.allowed()) {
            return Result.fail(HTTP_TOO_MANY_REQUESTS, isolationDecision.reasonMessage());
        }
        try {
            return Result.ok(multiAgentTraceService.replayArchivedTrace(
                    context.getUserId(),
                    archivedTrace.getTraceId(),
                    archivedTrace.getRequestSummary(),
                    new MultiAgentExecutionListener() {
                    }));
        } finally {
            agentRuntimeIsolationService.release(AGENT_TYPE_MULTI);
        }
    }

    @PostMapping("/logs/lifecycle/{agentType}/cleanup")
    public Result<AgentLogCleanupResponse> cleanupAgentLogs(
            @PathVariable(name = "agentType") String agentType,
            @RequestBody(required = false) AgentLogCleanupRequest body,
            HttpServletRequest request) {
        AgentRequestContext context = requestContextResolver.resolve(request);
        String deny = accessChecker.checkPermission(agentType, context.getRoles(), context.getDepartment());
        if (deny != null) {
            return Result.fail(HTTP_FORBIDDEN, deny);
        }
        boolean dryRun = body == null || body.isDryRun();
        return Result.ok(agentLogLifecycleService.cleanup(agentType, dryRun));
    }

    @GetMapping("/multi/traces/{traceId}")
    public Result<MultiAgentTraceResponse> getMultiAgentTrace(
            @PathVariable(name = "traceId") String traceId,
            HttpServletRequest request) {
        String userId = requestContextResolver.resolve(request).getUserId();
        MultiAgentTraceResponse trace = multiAgentTraceService.getTrace(userId, traceId);
        if (trace == null) {
            return Result.fail(404, "未找到对应的多智能体执行轨迹");
        }
        return Result.ok(trace);
    }

    @PostMapping("/multi/traces/{traceId}/recover")
    public Result<MultiAgentTraceResponse> recoverMultiAgentTrace(
            @PathVariable(name = "traceId") String traceId,
            @RequestBody MultiAgentTraceRecoverRequest body,
            HttpServletRequest request) {
        String userId = requestContextResolver.resolve(request).getUserId();
        AgentRuntimeIsolationService.RuntimeIsolationDecision isolationDecision = agentRuntimeIsolationService.acquire(AGENT_TYPE_MULTI);
        if (!isolationDecision.allowed()) {
            return Result.fail(HTTP_TOO_MANY_REQUESTS, isolationDecision.reasonMessage());
        }
        try {
            return Result.ok(multiAgentTraceService.recoverTrace(
                    userId,
                    traceId,
                    body != null ? body.getStepOrder() : null,
                    body != null ? body.getAction() : null,
                    new MultiAgentExecutionListener() {
                    }));
        } finally {
            agentRuntimeIsolationService.release(AGENT_TYPE_MULTI);
        }
    }

    @GetMapping("/multi/traces")
    public Result<List<MultiAgentTraceResponse>> listMultiAgentTraces(
            @org.springframework.web.bind.annotation.RequestParam(name = "sessionId", required = false) String sessionId,
            @org.springframework.web.bind.annotation.RequestParam(name = "limit", defaultValue = "20") Integer limit,
            HttpServletRequest request) {
        String userId = requestContextResolver.resolve(request).getUserId();
        int safeLimit = limit == null ? 20 : Math.max(1, Math.min(limit, 100));
        return Result.ok(multiAgentTraceService.listTraces(userId, sessionId, safeLimit));
    }

    private boolean ownsSession(String userId, String agentType, String sessionId) {
        return sessionId != null && sessionId.startsWith(buildSessionPrefix(userId, agentType));
    }

    private String buildSessionPrefix(String userId, String agentType) {
        return URLEncoder.encode(userId, StandardCharsets.UTF_8) + "-" + agentType + "-";
    }

    private String buildAgentDiagnosticSummary(String agentType,
                                               List<String> allowedTools,
                                               List<String> allowedConnectors,
                                               List<String> allowedMcpServers,
                                               int mcpServerCount,
                                               int mcpIssueCount,
                                               int recentMultiTraceCount) {
        List<String> fragments = new ArrayList<>();
        fragments.add("agent=" + agentType);
        fragments.add("tools=" + allowedTools.size());
        fragments.add("connectors=" + allowedConnectors.size());
        fragments.add("mcpServers=" + mcpServerCount);
        if (!allowedMcpServers.isEmpty()) {
            fragments.add("mcpAllow=" + allowedMcpServers.size());
        }
        if (mcpIssueCount > 0) {
            fragments.add("mcpIssues=" + mcpIssueCount);
        }
        if (recentMultiTraceCount > 0) {
            fragments.add("recentMultiTraces=" + recentMultiTraceCount);
        }
        return String.join(", ", fragments);
    }

    private AgentChatResult routeToAgent(String type, String userId, String sessionId, String message) {
        if (AGENT_TYPE_MULTI.equals(type)) {
            var result = multiAgentTraceService.execute(userId, sessionId, message, new MultiAgentExecutionListener() {
            });
            return new AgentChatResult(result.getContent(), result.getPromptTokens(), result.getCompletionTokens());
        }
        AssistantAgent assistantAgent = assistantAgentRegistry.getRequired(type);
        return assistantAgent.chat(userId, sessionId, message);
    }

    private Flux<ChatResponse> routeToAgentStream(String type, String userId, String sessionId, String message) {
        AssistantAgent assistantAgent = assistantAgentRegistry.getRequired(type);
        return assistantAgent.chatStream(userId, sessionId, message);
    }

    private void sendChunk(SseEmitter emitter, String chunk) {
        try {
            emitter.send(SseEmitter.event().data(Map.of("chunk", chunk, "done", false)));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    private void sendDone(SseEmitter emitter, String responseId) {
        sendDone(emitter, responseId, null);
    }

    private void sendDone(SseEmitter emitter, String responseId, String traceId) {
        try {
            emitter.send(SseEmitter.event().data(Map.of(
                    "chunk", "",
                    "done", true,
                    "responseId", responseId == null ? "" : responseId,
                    "traceId", traceId == null ? "" : traceId
            )));
            emitter.complete();
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) {
            return "null";
        }
        return text.length() <= maxLen
                ? text
                : text.substring(0, maxLen) + "...(truncated, total=" + text.length() + ")";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String saveAuditLog(String userId, String sessionId, String agentType,
                                String userMessage, String aiResponse, long latencyMs,
                                boolean success, String errorMessage,
                                long authLatencyMs, long preparationLatencyMs,
                                long modelLatencyMs, long persistenceLatencyMs,
                                int promptTokens, int completionTokens) {
        String logId = UUID.randomUUID().toString();
        try {
            String traceId = TraceIdContext.currentTraceId();
            auditLogMapper.insert(AiAuditLog.builder()
                    .id(logId)
                    .userId(userId)
                    .sessionId(sessionId)
                    .agentType(agentType)
                    .userMessage(userMessage != null && userMessage.length() > 500 ? userMessage.substring(0, 500) : userMessage)
                    .aiResponse(aiResponse != null && aiResponse.length() > 500 ? aiResponse.substring(0, 500) : aiResponse)
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .latencyMs(latencyMs)
                    .success(success)
                    .errorMessage(errorMessage)
                    .traceId(traceId)
                    .phaseBreakdownJson(buildPhaseBreakdownJson(traceId, latencyMs, authLatencyMs, preparationLatencyMs, modelLatencyMs, persistenceLatencyMs))
                    .createdAt(LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            log.warn("audit log write failed: {}", e.getMessage());
        }
        return logId;
    }

    private String buildPhaseBreakdownJson(String traceId,
                                           long totalLatencyMs,
                                           long authLatencyMs,
                                           long preparationLatencyMs,
                                           long modelLatencyMs,
                                           long persistenceLatencyMs) {
        if (totalLatencyMs <= 0) {
            return null;
        }

        long toolLatencyMs = getToolLatencyByTraceId(traceId);
        long generationLatencyMs = Math.max(0, modelLatencyMs - toolLatencyMs);
        long assigned = authLatencyMs + preparationLatencyMs + toolLatencyMs + generationLatencyMs + persistenceLatencyMs;
        if (assigned < totalLatencyMs) {
            generationLatencyMs += totalLatencyMs - assigned;
        }

        List<TracePhaseRecord> phases = new ArrayList<>();
        phases.add(buildPhaseRecord("auth", PHASE_AUTH_LABEL, authLatencyMs, totalLatencyMs, PHASE_AUTH_DESCRIPTION));
        phases.add(buildPhaseRecord("preparation", PHASE_PREPARATION_LABEL, preparationLatencyMs, totalLatencyMs, PHASE_PREPARATION_DESCRIPTION));
        phases.add(buildPhaseRecord("tools", PHASE_TOOLS_LABEL, toolLatencyMs, totalLatencyMs, PHASE_TOOLS_DESCRIPTION));
        phases.add(buildPhaseRecord("generation", PHASE_GENERATION_LABEL, generationLatencyMs, totalLatencyMs, PHASE_GENERATION_DESCRIPTION));
        phases.add(buildPhaseRecord("persistence", PHASE_PERSISTENCE_LABEL, persistenceLatencyMs, totalLatencyMs, PHASE_PERSISTENCE_DESCRIPTION));

        try {
            return objectMapper.writeValueAsString(phases);
        } catch (Exception e) {
            log.warn("serialize phase breakdown failed: {}", e.getMessage());
            return null;
        }
    }

    private TracePhaseRecord buildPhaseRecord(String key, String label, long latencyMs, long totalLatencyMs, String description) {
        long normalizedLatency = Math.max(latencyMs, 0);
        double share = totalLatencyMs <= 0 ? 0d : Math.round((normalizedLatency * 10000d / totalLatencyMs)) / 100d;
        return TracePhaseRecord.builder()
                .key(key)
                .label(label)
                .latencyMs(normalizedLatency)
                .share(share)
                .estimated(false)
                .description(description)
                .build();
    }

    private long getToolLatencyByTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return 0L;
        }
        try {
            return toolAuditLogMapper.selectList(
                            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AiToolAuditLog>()
                                    .eq("trace_id", traceId))
                    .stream()
                    .mapToLong(item -> Math.max(item.getLatencyMs() == null ? 0L : item.getLatencyMs(), 0L))
                    .sum();
        } catch (Exception e) {
            log.warn("load tool latency by traceId failed: {}", e.getMessage());
            return 0L;
        }
    }

    private static final class AgentControllerThreadFactory implements ThreadFactory {
        private final AtomicInteger sequence = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "agent-controller-" + sequence.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
