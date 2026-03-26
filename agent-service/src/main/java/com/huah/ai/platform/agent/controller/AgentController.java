package com.huah.ai.platform.agent.controller;

import com.huah.ai.platform.agent.audit.AiAuditLog;
import com.huah.ai.platform.agent.audit.AiAuditLogMapper;
import com.huah.ai.platform.agent.audit.AiToolAuditLog;
import com.huah.ai.platform.agent.audit.AiToolAuditLogMapper;
import com.huah.ai.platform.agent.audit.ResponseFeedbackService;
import com.huah.ai.platform.agent.audit.TracePhaseRecord;
import com.huah.ai.platform.agent.dto.AgentChatRequest;
import com.huah.ai.platform.agent.dto.AgentChatResponse;
import com.huah.ai.platform.agent.dto.McpServerListResponse;
import com.huah.ai.platform.agent.dto.MultiAgentTaskRequest;
import com.huah.ai.platform.agent.dto.ResponseFeedbackRequest;
import com.huah.ai.platform.agent.dto.SessionTitleRequest;
import com.huah.ai.platform.agent.dto.SessionConfigRequest;
import com.huah.ai.platform.agent.dto.SessionConfigResponse;
import com.huah.ai.platform.agent.dto.SessionToggleRequest;
import com.huah.ai.platform.agent.memory.ConversationMemoryService;
import com.huah.ai.platform.agent.metrics.AiMetricsCollector;
import com.huah.ai.platform.agent.multi.MultiAgentOrchestrator;
import com.huah.ai.platform.agent.security.AgentAccessChecker;
import com.huah.ai.platform.agent.service.AgentChatResult;
import com.huah.ai.platform.agent.service.AgentExecutionMetrics;
import com.huah.ai.platform.agent.service.AgentExecutionMetricsContext;
import com.huah.ai.platform.agent.service.AssistantAgent;
import com.huah.ai.platform.agent.service.AssistantAgentRegistry;
import com.huah.ai.platform.agent.service.McpServerCatalogService;
import com.huah.ai.platform.common.dto.Result;
import com.huah.ai.platform.common.trace.TraceIdContext;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;

@Slf4j
@RestController
@RequestMapping("/api/v1/agent")
@RequiredArgsConstructor
public class AgentController {

    private static final int PRE_DEDUCT_TOKENS = 500;

    private final AssistantAgentRegistry assistantAgentRegistry;
    private final MultiAgentOrchestrator multiAgentOrchestrator;
    private final ConversationMemoryService memoryService;
    private final AiAuditLogMapper auditLogMapper;
    private final AiToolAuditLogMapper toolAuditLogMapper;
    private final ResponseFeedbackService feedbackService;
    private final AgentAccessChecker accessChecker;
    private final AiMetricsCollector metricsCollector;
    private final AgentRequestContextResolver requestContextResolver;
    private final McpServerCatalogService mcpServerCatalogService;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @PostMapping("/{agentType}/chat")
    public Result<AgentChatResponse> chat(
            @PathVariable(name = "agentType") String agentType,
            @RequestBody AgentChatRequest body,
            @RequestHeader(value = "X-Session-Id", defaultValue = "default") String sessionId,
            HttpServletRequest request) {
        AgentRequestContext context = requestContextResolver.resolve(request);
        String userId = context.getUserId();
        String roles = context.getRoles();
        String department = context.getDepartment();

        String deny = accessChecker.checkPermission(agentType, roles, department);
        if (deny != null) {
            log.warn("[Chat] permission denied agent={}, userId={}, roles={}, reason={}", agentType, userId, roles, deny);
            return Result.fail(403, deny);
        }

        String quotaDeny = accessChecker.checkAndConsumeTokens(userId, agentType, PRE_DEDUCT_TOKENS);
        if (quotaDeny != null) {
            log.warn("[Chat] token quota exceeded agent={}, userId={}", agentType, userId);
            return Result.fail(429, quotaDeny);
        }

        String message = body.getMessage();
        if (message == null || message.isBlank()) {
            return Result.fail(400, "message 不能为空");
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
            AgentChatResult result = routeToAgent(agentType, userId, sessionId, message);
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
                    .build());
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            metricsCollector.recordModelCall("multi", latency, false);
            metricsCollector.recordRequest(null, "multi", latency, false, 0, 0);
            metricsCollector.recordModelCall(agentType, latency, false);
            metricsCollector.recordRequest(null, agentType, latency, false, 0, 0);
            log.error("[Chat] failed agent={}, userId={}, latency={}ms, error={}",
                    agentType, userId, latency, e.getMessage(), e);
            accessChecker.recordActualTokens(userId, 0, PRE_DEDUCT_TOKENS);
            memoryService.rollbackLastUserMessage(sessionId);
            return Result.fail(500, "AI 服务暂时不可用，请稍后重试");
        }
    }

    @PostMapping(value = "/{agentType}/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(
            @PathVariable(name = "agentType") String agentType,
            @RequestBody AgentChatRequest body,
            @RequestHeader(value = "X-Session-Id", defaultValue = "default") String sessionId,
            HttpServletRequest request) {

        SseEmitter emitter = new SseEmitter(180_000L);
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
                if ("multi".equals(agentType)) {
                    log.info("[Stream] multi-agent started userId={}", userId);
                    String internalId = sessionId + "-";
                    int totalPrompt = 0;
                    int totalCompletion = 0;

                    sendChunk(emitter, "**[Planner] 正在分析任务...**\n\n");
                    var planResult = multiAgentOrchestrator.planTask(message, internalId + "-planner");
                    sendChunk(emitter, planResult.getContent() + "\n\n---\n\n");
                    totalPrompt += planResult.getPromptTokens();
                    totalCompletion += planResult.getCompletionTokens();

                    sendChunk(emitter, "**[Executor] 正在执行任务...**\n\n");
                    var execResult = multiAgentOrchestrator.executeWithTools(message, planResult.getContent(), internalId + "-executor");
                    sendChunk(emitter, execResult.getContent() + "\n\n---\n\n");
                    totalPrompt += execResult.getPromptTokens();
                    totalCompletion += execResult.getCompletionTokens();

                    sendChunk(emitter, "**[Critic] 正在审查结果...**\n\n");
                    var criticResult = multiAgentOrchestrator.critique(message, execResult.getContent(), internalId + "-critic");
                    sendChunk(emitter, criticResult.getContent());
                    totalPrompt += criticResult.getPromptTokens();
                    totalCompletion += criticResult.getCompletionTokens();

                    memoryService.saveExchange(sessionId, message, criticResult.getContent());

                    metricsCollector.decrementActive();
                    long latency = System.currentTimeMillis() - startTime;
                    metricsCollector.recordModelCall("multi", latency, true);
                    metricsCollector.recordRequest(null, "multi", latency, true, totalPrompt, totalCompletion);
                    log.info("[Stream] multi-agent completed userId={}, latency={}ms, tokens={}/{}",
                            userId, latency, totalPrompt, totalCompletion);
                    String responseId = saveAuditLog(userId, sessionId, "multi", message, truncate(criticResult.getContent(), 500),
                            latency, true, null, 0L, 0L, latency, 0L, totalPrompt, totalCompletion);
                    accessChecker.recordActualTokens(userId, totalPrompt + totalCompletion, PRE_DEDUCT_TOKENS);
                    sendDone(emitter, responseId);
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
                if (!"multi".equals(agentType)) {
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
        });

        return emitter;
    }

    @PostMapping("/multi/execute")
    public Result<String> multiAgentExecute(
            @RequestBody MultiAgentTaskRequest body,
            @RequestHeader(value = "X-Session-Id", defaultValue = "default") String sessionId,
            HttpServletRequest request) {
        AgentRequestContext context = requestContextResolver.resolve(request);
        String userId = context.getUserId();
        String roles = context.getRoles();
        String department = context.getDepartment();

        String deny = accessChecker.checkPermission("multi", roles, department);
        if (deny != null) {
            return Result.fail(403, deny);
        }

        String task = body.getTask();
        if (task == null || task.isBlank()) {
            return Result.fail(400, "task 不能为空");
        }

        log.info("[Multi] received userId={}, taskLength={}", userId, task.length());
        log.info("[Multi] input userId={}, task={}", userId, truncate(task, 500));

        long startTime = System.currentTimeMillis();
        try {
            var result = multiAgentOrchestrator.executeComplexTask(userId, sessionId, task);
            long latency = System.currentTimeMillis() - startTime;
            metricsCollector.recordModelCall("multi", latency, true);
            metricsCollector.recordRequest(null, "multi", latency, true,
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
            @RequestHeader(value = "X-Session-Id", defaultValue = "default") String sessionId,
            HttpServletRequest request) {
        String userId = requestContextResolver.resolve(request).getUserId();
        if (!ownsSession(userId, agentType, sessionId)) {
            return Result.fail(403, "无权清除该会话");
        }
        log.info("[Memory] clear agent={}, userId={}, sessionId={}", agentType, userId, sessionId);
        memoryService.clearMemory(sessionId);
        return Result.ok("会话记忆已清除");
    }

    @GetMapping("/{agentType}/memory")
    public Result<List<Map<String, String>>> getHistory(
            @PathVariable(name = "agentType") String agentType,
            @RequestHeader(value = "X-Session-Id", defaultValue = "default") String sessionId,
            HttpServletRequest request) {
        String userId = requestContextResolver.resolve(request).getUserId();
        if (!ownsSession(userId, agentType, sessionId)) {
            return Result.fail(403, "无权查看该会话");
        }
        log.info("[Memory] history agent={}, userId={}, sessionId={}", agentType, userId, sessionId);
        return Result.ok(memoryService.getHistory(sessionId));
    }

    @GetMapping("/{agentType}/sessions")
    public Result<List<Map<String, String>>> listSessions(
            @PathVariable(name = "agentType") String agentType,
            @RequestHeader(value = "X-Session-Id", required = false) String ignoredSessionId,
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
            @RequestHeader(value = "X-Session-Id") String sessionId,
            HttpServletRequest request) {
        String userId = requestContextResolver.resolve(request).getUserId();
        if (!ownsSession(userId, agentType, sessionId)) {
            return Result.fail(403, "无权查看该会话配置");
        }
        return Result.ok(memoryService.getSessionConfig(sessionId));
    }

    @PostMapping("/{agentType}/sessions/config")
    public Result<String> saveSessionConfig(
            @PathVariable(name = "agentType") String agentType,
            @RequestHeader(value = "X-Session-Id") String sessionId,
            @RequestBody SessionConfigRequest body,
            HttpServletRequest request) {
        String userId = requestContextResolver.resolve(request).getUserId();
        if (!ownsSession(userId, agentType, sessionId)) {
            return Result.fail(403, "无权修改该会话配置");
        }
        memoryService.saveSessionConfig(sessionId, body);
        return Result.ok("会话配置已更新");
    }

    @PostMapping("/{agentType}/sessions/title")
    public Result<String> renameSessionTitle(
            @PathVariable(name = "agentType") String agentType,
            @RequestHeader(value = "X-Session-Id") String sessionId,
            @RequestBody SessionTitleRequest body,
            HttpServletRequest request) {
        String userId = requestContextResolver.resolve(request).getUserId();
        if (!ownsSession(userId, agentType, sessionId)) {
            return Result.fail(403, "无权修改该会话");
        }

        String title = body.getTitle();
        if (title == null || title.isBlank()) {
            return Result.fail(400, "title 不能为空");
        }

        memoryService.renameSession(sessionId, title);
        return Result.ok("会话标题已更新");
    }

    @PostMapping("/{agentType}/sessions/pin")
    public Result<String> pinSession(
            @PathVariable(name = "agentType") String agentType,
            @RequestHeader(value = "X-Session-Id") String sessionId,
            @RequestBody SessionToggleRequest body,
            HttpServletRequest request) {
        String userId = requestContextResolver.resolve(request).getUserId();
        if (!ownsSession(userId, agentType, sessionId)) {
            return Result.fail(403, "无权修改该会话");
        }

        boolean pinned = Boolean.TRUE.equals(body.getPinned());
        memoryService.pinSession(sessionId, pinned);
        return Result.ok(pinned ? "会话已置顶" : "会话已取消置顶");
    }

    @PostMapping("/{agentType}/sessions/archive")
    public Result<String> archiveSession(
            @PathVariable(name = "agentType") String agentType,
            @RequestHeader(value = "X-Session-Id") String sessionId,
            @RequestBody SessionToggleRequest body,
            HttpServletRequest request) {
        String userId = requestContextResolver.resolve(request).getUserId();
        if (!ownsSession(userId, agentType, sessionId)) {
            return Result.fail(403, "无权修改该会话");
        }

        boolean archived = Boolean.TRUE.equals(body.getArchived());
        memoryService.archiveSession(sessionId, archived);
        return Result.ok(archived ? "会话已归档" : "会话已取消归档");
    }

    @DeleteMapping("/{agentType}/sessions")
    public Result<String> deleteSession(
            @PathVariable(name = "agentType") String agentType,
            @RequestHeader(value = "X-Session-Id") String sessionId,
            HttpServletRequest request) {
        String userId = requestContextResolver.resolve(request).getUserId();
        if (!ownsSession(userId, agentType, sessionId)) {
            return Result.fail(403, "无权删除该会话");
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

    @GetMapping("/tools/audit")
    public Result<List<AiToolAuditLog>> listToolAuditLogs(
            @org.springframework.web.bind.annotation.RequestParam(name = "agentType", required = false) String agentType,
            @org.springframework.web.bind.annotation.RequestParam(name = "toolName", required = false) String toolName,
            @org.springframework.web.bind.annotation.RequestParam(name = "limit", defaultValue = "50") Integer limit,
            HttpServletRequest request) {
        String userId = requestContextResolver.resolve(request).getUserId();
        int safeLimit = limit == null ? 50 : Math.max(1, Math.min(limit, 200));
        return Result.ok(toolAuditLogMapper.selectRecent(userId, agentType, toolName, safeLimit));
    }

    private boolean ownsSession(String userId, String agentType, String sessionId) {
        return sessionId != null && sessionId.startsWith(buildSessionPrefix(userId, agentType));
    }

    private String buildSessionPrefix(String userId, String agentType) {
        return URLEncoder.encode(userId, StandardCharsets.UTF_8) + "-" + agentType + "-";
    }

    private AgentChatResult routeToAgent(String type, String userId, String sessionId, String message) {
        if ("multi".equals(type)) {
            var result = multiAgentOrchestrator.executeComplexTask(userId, sessionId, message);
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
        try {
            emitter.send(SseEmitter.event().data(Map.of(
                    "chunk", "",
                    "done", true,
                    "responseId", responseId == null ? "" : responseId
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
        phases.add(buildPhaseRecord("auth", "鉴权与上下文", authLatencyMs, totalLatencyMs, "请求进入控制器、权限校验和配额检查。"));
        phases.add(buildPhaseRecord("preparation", "请求准备", preparationLatencyMs, totalLatencyMs, "会话配置读取、提示词拼装和模型请求准备。"));
        phases.add(buildPhaseRecord("tools", "工具执行", toolLatencyMs, totalLatencyMs, "来自工具审计日志的真实工具执行耗时。"));
        phases.add(buildPhaseRecord("generation", "模型生成", generationLatencyMs, totalLatencyMs, "模型推理与流式或非流式回复生成。"));
        phases.add(buildPhaseRecord("persistence", "落库与审计", persistenceLatencyMs, totalLatencyMs, "审计日志写入与响应收尾。"));

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
}
