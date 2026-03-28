package com.huah.ai.platform.agent.controller;

import com.huah.ai.platform.agent.dto.AgentChatRequest;
import com.huah.ai.platform.agent.dto.AgentChatResponse;
import com.huah.ai.platform.agent.dto.MultiAgentTaskRequest;
import com.huah.ai.platform.agent.memory.ConversationMemoryService;
import com.huah.ai.platform.agent.metrics.AiMetricsCollector;
import com.huah.ai.platform.agent.multi.MultiAgentExecutionListener;
import com.huah.ai.platform.agent.multi.MultiAgentExecutionResult;
import com.huah.ai.platform.agent.multi.MultiAgentExecutionStep;
import com.huah.ai.platform.agent.multi.MultiAgentTraceService;
import com.huah.ai.platform.agent.security.AgentAccessChecker;
import com.huah.ai.platform.agent.security.ToolAccessDeniedException;
import com.huah.ai.platform.agent.service.AgentAuditLogService;
import com.huah.ai.platform.agent.service.AgentChatResult;
import com.huah.ai.platform.agent.service.AgentExecutionMetrics;
import com.huah.ai.platform.agent.service.AgentExecutionMetricsContext;
import com.huah.ai.platform.agent.service.AgentRuntimeIsolationService;
import com.huah.ai.platform.agent.service.AssistantAgent;
import com.huah.ai.platform.agent.service.AssistantAgentRegistry;
import com.huah.ai.platform.common.dto.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@RestController
@RequestMapping(AgentApiConstants.BASE_PATH)
@RequiredArgsConstructor
public class AgentConversationController {

    private final AssistantAgentRegistry assistantAgentRegistry;
    private final MultiAgentTraceService multiAgentTraceService;
    private final ConversationMemoryService memoryService;
    private final AgentAccessChecker accessChecker;
    private final AiMetricsCollector metricsCollector;
    private final AgentControllerSupport controllerSupport;
    private final AgentRuntimeIsolationService agentRuntimeIsolationService;
    private final AgentAuditLogService agentAuditLogService;
    @Qualifier("agentControllerExecutor")
    private final ExecutorService executor;

    @PostMapping("/{agentType}/chat")
    public Result<AgentChatResponse> chat(
            @PathVariable(name = "agentType") String agentType,
            @RequestBody AgentChatRequest body,
            @RequestHeader(value = AgentApiConstants.HEADER_SESSION_ID,
                    defaultValue = AgentApiConstants.DEFAULT_SESSION_ID) String sessionId,
            HttpServletRequest request) {
        AgentRequestContext context = controllerSupport.resolveContext(request);
        String userId = context.getUserId();

        String deny = accessChecker.checkPermission(agentType, context.getRoles(), context.getDepartment());
        if (deny != null) {
            log.warn("[Chat] permission denied agent={}, userId={}, roles={}, reason={}",
                    agentType, userId, context.getRoles(), deny);
            return Result.fail(AgentApiConstants.HTTP_FORBIDDEN, deny);
        }

        String quotaDeny = accessChecker.checkAndConsumeTokens(userId, agentType, AgentApiConstants.PRE_DEDUCT_TOKENS);
        if (quotaDeny != null) {
            log.warn("[Chat] token quota exceeded agent={}, userId={}", agentType, userId);
            return Result.fail(AgentApiConstants.HTTP_TOO_MANY_REQUESTS, quotaDeny);
        }
        AgentRuntimeIsolationService.RuntimeIsolationDecision isolationDecision =
                agentRuntimeIsolationService.acquire(agentType);
        if (!isolationDecision.allowed()) {
            return Result.fail(AgentApiConstants.HTTP_TOO_MANY_REQUESTS, isolationDecision.reasonMessage());
        }

        String message = body.getMessage();
        if (message == null || message.isBlank()) {
            return Result.fail(AgentApiConstants.HTTP_BAD_REQUEST, AgentApiConstants.MESSAGE_MESSAGE_REQUIRED);
        }
        if (body.getSessionConfig() != null) {
            memoryService.saveSessionConfig(sessionId, body.getSessionConfig());
        }

        log.info("[Chat] received agent={}, userId={}, sessionId={}, messageLength={}",
                agentType, userId, sessionId, message.length());
        log.info("[Chat] input agent={}, userId={}, message={}",
                agentType, userId, controllerSupport.truncate(message, 500));

        long startTime = System.currentTimeMillis();
        try {
            long agentStartTime = System.currentTimeMillis();
            AgentChatResult result;
            String traceId = null;
            if (AgentApiConstants.AGENT_TYPE_MULTI.equals(agentType)) {
                MultiAgentExecutionResult multiResult = multiAgentTraceService.execute(
                        userId, sessionId, message, new MultiAgentExecutionListener() { });
                result = new AgentChatResult(
                        multiResult.getContent(), multiResult.getPromptTokens(), multiResult.getCompletionTokens());
                traceId = multiResult.getTraceId();
            } else {
                result = routeToAgent(agentType, userId, sessionId, message);
            }
            long latency = System.currentTimeMillis() - startTime;
            long persistenceStartTime = System.currentTimeMillis();
            AgentExecutionMetrics executionMetrics = result.getExecutionMetrics();
            Long responseId = agentAuditLogService.saveAuditLog(
                    userId,
                    sessionId,
                    agentType,
                    message,
                    result.getContent(),
                    latency,
                    true,
                    null,
                    Math.max(0, agentStartTime - startTime),
                    executionMetrics != null ? executionMetrics.getPreparationLatencyMs() : 0L,
                    executionMetrics != null
                            ? executionMetrics.getModelLatencyMs()
                            : Math.max(0, System.currentTimeMillis() - agentStartTime),
                    Math.max(0, System.currentTimeMillis() - persistenceStartTime),
                    result.getPromptTokens(),
                    result.getCompletionTokens());
            metricsCollector.recordModelCall(agentType, latency, true);
            metricsCollector.recordRequest(
                    null, agentType, latency, true, result.getPromptTokens(), result.getCompletionTokens());
            log.info("[Chat] output agent={}, userId={}, latency={}ms, responseLength={}, promptTokens={}, completionTokens={}, response={}",
                    agentType,
                    userId,
                    latency,
                    result.getContent() != null ? result.getContent().length() : 0,
                    result.getPromptTokens(),
                    result.getCompletionTokens(),
                    controllerSupport.truncate(result.getContent(), 500));
            accessChecker.recordActualTokens(
                    userId,
                    result.getPromptTokens() + result.getCompletionTokens(),
                    AgentApiConstants.PRE_DEDUCT_TOKENS);
            return Result.ok(AgentChatResponse.builder()
                    .responseId(responseId)
                    .content(result.getContent())
                    .traceId(traceId)
                    .build());
        } catch (ToolAccessDeniedException e) {
            long latency = System.currentTimeMillis() - startTime;
            metricsCollector.recordModelCall(agentType, latency, false);
            metricsCollector.recordRequest(null, agentType, latency, false, 0, 0);
            accessChecker.recordActualTokens(userId, 0, AgentApiConstants.PRE_DEDUCT_TOKENS);
            memoryService.rollbackLastUserMessage(sessionId);
            throw e;
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            metricsCollector.recordModelCall(AgentApiConstants.AGENT_TYPE_MULTI, latency, false);
            metricsCollector.recordRequest(null, AgentApiConstants.AGENT_TYPE_MULTI, latency, false, 0, 0);
            metricsCollector.recordModelCall(agentType, latency, false);
            metricsCollector.recordRequest(null, agentType, latency, false, 0, 0);
            log.error("[Chat] failed agent={}, userId={}, latency={}ms, error={}",
                    agentType, userId, latency, e.getMessage(), e);
            accessChecker.recordActualTokens(userId, 0, AgentApiConstants.PRE_DEDUCT_TOKENS);
            memoryService.rollbackLastUserMessage(sessionId);
            return Result.fail(AgentApiConstants.HTTP_INTERNAL_SERVER_ERROR, AgentApiConstants.MESSAGE_AI_UNAVAILABLE);
        } finally {
            agentRuntimeIsolationService.release(agentType);
        }
    }

    @PostMapping(value = "/{agentType}/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(
            @PathVariable(name = "agentType") String agentType,
            @RequestBody AgentChatRequest body,
            @RequestHeader(value = AgentApiConstants.HEADER_SESSION_ID,
                    defaultValue = AgentApiConstants.DEFAULT_SESSION_ID) String sessionId,
            HttpServletRequest request) {
        long streamTimeoutMs = agentRuntimeIsolationService.getStreamTimeoutMs(agentType, 180_000L);
        SseEmitter emitter = new SseEmitter(streamTimeoutMs);
        AgentRequestContext context = controllerSupport.resolveContext(request);
        String userId = context.getUserId();

        String deny = accessChecker.checkPermission(agentType, context.getRoles(), context.getDepartment());
        if (deny != null) {
            log.warn("[Stream] permission denied agent={}, userId={}, roles={}, reason={}",
                    agentType, userId, context.getRoles(), deny);
            sendChunk(emitter, AgentApiConstants.MESSAGE_STREAM_PERMISSION_DENIED + deny);
            sendDone(emitter, null, null);
            return emitter;
        }

        String quotaDeny = accessChecker.checkAndConsumeTokens(userId, agentType, AgentApiConstants.PRE_DEDUCT_TOKENS);
        if (quotaDeny != null) {
            log.warn("[Stream] token quota exceeded agent={}, userId={}", agentType, userId);
            sendChunk(emitter, AgentApiConstants.MESSAGE_STREAM_QUOTA_DENIED + quotaDeny);
            sendDone(emitter, null, null);
            return emitter;
        }

        String message = body.getMessage();
        if (message == null || message.isBlank()) {
            log.warn("[Stream] empty message, agent={}, userId={}", agentType, userId);
            sendChunk(emitter, AgentApiConstants.MESSAGE_MESSAGE_REQUIRED);
            sendDone(emitter, null, null);
            return emitter;
        }
        if (body.getSessionConfig() != null) {
            memoryService.saveSessionConfig(sessionId, body.getSessionConfig());
        }

        AgentRuntimeIsolationService.RuntimeIsolationDecision streamIsolationDecision =
                agentRuntimeIsolationService.acquire(agentType);
        if (!streamIsolationDecision.allowed()) {
            sendChunk(emitter, AgentApiConstants.MESSAGE_STREAM_RUNTIME_ISOLATION
                    + streamIsolationDecision.reasonMessage());
            sendDone(emitter, null, null);
            return emitter;
        }

        log.info("[Stream] received agent={}, userId={}, sessionId={}, messageLength={}",
                agentType, userId, sessionId, message.length());
        log.info("[Stream] input agent={}, userId={}, message={}",
                agentType, userId, controllerSupport.truncate(message, 500));

        long startTime = System.currentTimeMillis();
        AtomicInteger chunkCount = new AtomicInteger(0);
        StringBuilder fullResponse = new StringBuilder();
        AtomicInteger totalPromptTokens = new AtomicInteger(0);
        AtomicInteger totalCompletionTokens = new AtomicInteger(0);

        metricsCollector.incrementActive();

        executor.submit(() -> {
            try {
                if (AgentApiConstants.AGENT_TYPE_MULTI.equals(agentType)) {
                    handleMultiAgentStream(emitter, userId, sessionId, message, startTime, chunkCount, fullResponse);
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
                            }
                            if (chunk == null) {
                                chunk = "";
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
                            metricsCollector.recordRequest(
                                    null, agentType, latency, true, totalPromptTokens.get(), totalCompletionTokens.get());
                            String response = fullResponse.toString();
                            log.info("[Stream] output agent={}, userId={}, latency={}ms, chunks={}, responseLength={}, promptTokens={}, completionTokens={}, response={}",
                                    agentType,
                                    userId,
                                    latency,
                                    chunkCount.get(),
                                    response.length(),
                                    totalPromptTokens.get(),
                                    totalCompletionTokens.get(),
                                    controllerSupport.truncate(response, 500));
                            accessChecker.recordActualTokens(
                                    userId,
                                    totalPromptTokens.get() + totalCompletionTokens.get(),
                                    AgentApiConstants.PRE_DEDUCT_TOKENS);
                            Long responseId = agentAuditLogService.saveAuditLog(
                                    userId,
                                    sessionId,
                                    agentType,
                                    message,
                                    response,
                                    latency,
                                    true,
                                    null,
                                    Math.max(0, agentStartTime - startTime),
                                    executionMetrics != null ? executionMetrics.getPreparationLatencyMs() : 0L,
                                    Math.max(0, System.currentTimeMillis() - agentStartTime),
                                    Math.max(0, System.currentTimeMillis() - persistenceStartTime),
                                    totalPromptTokens.get(),
                                    totalCompletionTokens.get());
                            sendDone(emitter, responseId, null);
                        })
                        .doOnError(error -> {
                            metricsCollector.decrementActive();
                            long latency = System.currentTimeMillis() - startTime;
                            long persistenceStartTime = System.currentTimeMillis();
                            metricsCollector.recordModelCall(agentType, latency, false);
                            metricsCollector.recordRequest(null, agentType, latency, false, 0, 0);
                            log.error("[Stream] failed agent={}, userId={}, latency={}ms, chunks={}, partialResponse={}, error={}",
                                    agentType,
                                    userId,
                                    latency,
                                    chunkCount.get(),
                                    controllerSupport.truncate(fullResponse.toString(), 200),
                                    error.getMessage(),
                                    error);
                            accessChecker.recordActualTokens(userId, 0, AgentApiConstants.PRE_DEDUCT_TOKENS);
                            agentAuditLogService.saveAuditLog(
                                    userId,
                                    sessionId,
                                    agentType,
                                    message,
                                    null,
                                    latency,
                                    false,
                                    error.getMessage(),
                                    Math.max(0, agentStartTime - startTime),
                                    executionMetrics != null ? executionMetrics.getPreparationLatencyMs() : 0L,
                                    Math.max(0, System.currentTimeMillis() - agentStartTime),
                                    Math.max(0, System.currentTimeMillis() - persistenceStartTime),
                                    0,
                                    0);
                            memoryService.rollbackLastUserMessage(sessionId);
                            sendChunk(emitter, AgentApiConstants.MESSAGE_STREAM_AI_UNAVAILABLE);
                            sendDone(emitter, null, null);
                        })
                        .subscribe();
            } catch (Exception e) {
                metricsCollector.decrementActive();
                long latency = System.currentTimeMillis() - startTime;
                metricsCollector.recordModelCall(agentType, latency, false);
                metricsCollector.recordRequest(null, agentType, latency, false, 0, 0);
                log.error("[Stream] execution failed agent={}, userId={}, latency={}ms, error={}",
                        agentType, userId, latency, e.getMessage(), e);
                accessChecker.recordActualTokens(userId, 0, AgentApiConstants.PRE_DEDUCT_TOKENS);
                if (!AgentApiConstants.AGENT_TYPE_MULTI.equals(agentType)) {
                    memoryService.rollbackLastUserMessage(sessionId);
                }
                sendChunk(emitter, AgentApiConstants.MESSAGE_STREAM_AI_UNAVAILABLE);
                sendDone(emitter, null, null);
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
            @RequestHeader(value = AgentApiConstants.HEADER_SESSION_ID,
                    defaultValue = AgentApiConstants.DEFAULT_SESSION_ID) String sessionId,
            HttpServletRequest request) {
        AgentRequestContext context = controllerSupport.resolveContext(request);
        String userId = context.getUserId();

        String deny = accessChecker.checkPermission(
                AgentApiConstants.AGENT_TYPE_MULTI, context.getRoles(), context.getDepartment());
        if (deny != null) {
            return Result.fail(AgentApiConstants.HTTP_FORBIDDEN, deny);
        }

        String task = body.getTask();
        if (task == null || task.isBlank()) {
            return Result.fail(AgentApiConstants.HTTP_BAD_REQUEST, AgentApiConstants.MESSAGE_TASK_REQUIRED);
        }

        log.info("[Multi] received userId={}, taskLength={}", userId, task.length());
        log.info("[Multi] input userId={}, task={}", userId, controllerSupport.truncate(task, 500));

        long startTime = System.currentTimeMillis();
        try {
            MultiAgentExecutionResult result = multiAgentTraceService.execute(
                    userId, sessionId, task, new MultiAgentExecutionListener() { });
            long latency = System.currentTimeMillis() - startTime;
            metricsCollector.recordModelCall(AgentApiConstants.AGENT_TYPE_MULTI, latency, true);
            metricsCollector.recordRequest(
                    null,
                    AgentApiConstants.AGENT_TYPE_MULTI,
                    latency,
                    true,
                    result.getPromptTokens(),
                    result.getCompletionTokens());
            log.info("[Multi] output userId={}, latency={}ms, responseLength={}, tokens={}/{}, response={}",
                    userId,
                    latency,
                    result.getContent() != null ? result.getContent().length() : 0,
                    result.getPromptTokens(),
                    result.getCompletionTokens(),
                    controllerSupport.truncate(result.getContent(), 500));
            return Result.ok(result.getContent());
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            log.error("[Multi] failed userId={}, latency={}ms, error={}",
                    userId, latency, e.getMessage(), e);
            throw e;
        }
    }

    private void handleMultiAgentStream(SseEmitter emitter,
                                        String userId,
                                        String sessionId,
                                        String message,
                                        long startTime,
                                        AtomicInteger chunkCount,
                                        StringBuilder fullResponse) {
        log.info("[Stream] multi-agent started userId={}", userId);
        MultiAgentExecutionResult multiResult = multiAgentTraceService.execute(
                userId,
                sessionId,
                message,
                new MultiAgentExecutionListener() {
                    @Override
                    public void onStageStarted(String stage, String label) {
                        sendChunk(emitter, "**[" + label + "]" + AgentApiConstants.MESSAGE_STREAM_STAGE_RUNNING + "**\n\n");
                    }

                    @Override
                    public void onStageCompleted(MultiAgentExecutionStep step) {
                        if (Boolean.TRUE.equals(step.getSuccess())) {
                            String output = step.getOutputSummary() == null ? "" : step.getOutputSummary();
                            fullResponse.append(output);
                            chunkCount.incrementAndGet();
                            String suffix = "critic".equals(step.getStage()) ? "" : "\n\n---\n\n";
                            sendChunk(emitter, output + suffix);
                        }
                    }

                    @Override
                    public void onFailed(String stage, String errorMessage) {
                        log.warn("[Stream] multi-agent stage failed userId={}, stage={}, error={}",
                                userId, stage, errorMessage);
                    }
                });

        metricsCollector.decrementActive();
        long latency = System.currentTimeMillis() - startTime;
        metricsCollector.recordModelCall(AgentApiConstants.AGENT_TYPE_MULTI, latency, true);
        metricsCollector.recordRequest(
                null,
                AgentApiConstants.AGENT_TYPE_MULTI,
                latency,
                true,
                multiResult.getPromptTokens(),
                multiResult.getCompletionTokens());
        log.info("[Stream] multi-agent completed userId={}, latency={}ms, chunks={}, tokens={}/{}",
                userId, latency, chunkCount.get(), multiResult.getPromptTokens(), multiResult.getCompletionTokens());
        Long responseId = agentAuditLogService.saveAuditLog(
                userId,
                sessionId,
                AgentApiConstants.AGENT_TYPE_MULTI,
                message,
                controllerSupport.truncate(multiResult.getContent(), 500),
                latency,
                true,
                null,
                0L,
                0L,
                latency,
                0L,
                multiResult.getPromptTokens(),
                multiResult.getCompletionTokens());
        accessChecker.recordActualTokens(
                userId,
                multiResult.getPromptTokens() + multiResult.getCompletionTokens(),
                AgentApiConstants.PRE_DEDUCT_TOKENS);
        sendDone(emitter, responseId, multiResult.getTraceId());
    }

    private AgentChatResult routeToAgent(String type, String userId, String sessionId, String message) {
        if (AgentApiConstants.AGENT_TYPE_MULTI.equals(type)) {
            MultiAgentExecutionResult result = multiAgentTraceService.execute(
                    userId, sessionId, message, new MultiAgentExecutionListener() { });
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

    private void sendDone(SseEmitter emitter, Long responseId, String traceId) {
        try {
            emitter.send(SseEmitter.event().data(Map.of(
                    "chunk", "",
                    "done", true,
                    "responseId", responseId == null ? "" : String.valueOf(responseId),
                    "traceId", traceId == null ? "" : traceId
            )));
            emitter.complete();
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }
}
