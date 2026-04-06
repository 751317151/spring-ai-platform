package com.huah.ai.platform.agent.service;

import com.huah.ai.platform.agent.controller.AgentApiConstants;
import com.huah.ai.platform.agent.dto.AgentChatResponse;
import com.huah.ai.platform.agent.memory.ConversationMemoryService;
import com.huah.ai.platform.agent.metrics.AiMetricsCollector;
import com.huah.ai.platform.agent.multi.MultiAgentExecutionListener;
import com.huah.ai.platform.agent.multi.MultiAgentExecutionResult;
import com.huah.ai.platform.agent.multi.MultiAgentExecutionStep;
import com.huah.ai.platform.agent.multi.MultiAgentTraceService;
import com.huah.ai.platform.agent.security.AgentAccessChecker;
import com.huah.ai.platform.agent.security.ToolAccessDeniedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentConversationOrchestrator {

    private final AssistantAgentRegistry assistantAgentRegistry;
    private final MultiAgentTraceService multiAgentTraceService;
    private final ConversationMemoryService memoryService;
    private final AgentAccessChecker accessChecker;
    private final AiMetricsCollector metricsCollector;
    private final AgentAuditLogService agentAuditLogService;
    @Qualifier("agentControllerExecutor")
    private final ExecutorService agentControllerExecutor;

    public AgentChatResponse executeChat(String agentType, String userId, String sessionId, String message) {
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
            recordSuccessMetrics(agentType, latency, result.getPromptTokens(), result.getCompletionTokens());
            log.info("[Chat] output agent={}, userId={}, latency={}ms, responseLength={}, promptTokens={}, completionTokens={}, response={}",
                    agentType,
                    userId,
                    latency,
                    result.getContent() != null ? result.getContent().length() : 0,
                    result.getPromptTokens(),
                    result.getCompletionTokens(),
                    truncate(result.getContent(), 500));
            accessChecker.recordActualTokens(
                    userId,
                    agentType,
                    result.getPromptTokens() + result.getCompletionTokens(),
                    AgentApiConstants.PRE_DEDUCT_TOKENS);
            return AgentChatResponse.builder()
                    .responseId(responseId)
                    .content(result.getContent())
                    .traceId(traceId)
                    .build();
        } catch (ToolAccessDeniedException e) {
            handleChatFailure(agentType, userId, sessionId, startTime, false);
            throw e;
        } catch (RuntimeException e) {
            handleChatFailure(agentType, userId, sessionId, startTime, true);
            log.error("[Chat] failed agent={}, userId={}, latency={}ms, error={}",
                    agentType, userId, System.currentTimeMillis() - startTime, e.getMessage(), e);
            throw e;
        }
    }

    public void executeStream(String agentType, String userId, String sessionId, String message, SseEmitter emitter) {
        long startTime = System.currentTimeMillis();
        AtomicInteger chunkCount = new AtomicInteger(0);
        StringBuilder fullResponse = new StringBuilder();
        AtomicInteger totalPromptTokens = new AtomicInteger(0);
        AtomicInteger totalCompletionTokens = new AtomicInteger(0);

        metricsCollector.incrementActive();

        agentControllerExecutor.submit(() -> {
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
                        .doOnComplete(() -> completeStreamSuccess(
                                emitter,
                                agentType,
                                userId,
                                sessionId,
                                message,
                                startTime,
                                agentStartTime,
                                chunkCount,
                                fullResponse,
                                totalPromptTokens.get(),
                                totalCompletionTokens.get(),
                                executionMetrics
                        ))
                        .doOnError(error -> failStreamExecution(
                                emitter,
                                agentType,
                                userId,
                                sessionId,
                                message,
                                startTime,
                                agentStartTime,
                                chunkCount,
                                fullResponse,
                                executionMetrics,
                                error
                        ))
                        .subscribe();
            } catch (Exception e) {
                metricsCollector.decrementActive();
                long latency = System.currentTimeMillis() - startTime;
                recordFailureMetrics(agentType, latency, false);
                log.error("[Stream] execution failed agent={}, userId={}, latency={}ms, error={}",
                        agentType, userId, latency, e.getMessage(), e);
                accessChecker.recordActualTokens(userId, agentType, 0, AgentApiConstants.PRE_DEDUCT_TOKENS);
                if (!AgentApiConstants.AGENT_TYPE_MULTI.equals(agentType)) {
                    memoryService.rollbackLastUserMessage(sessionId);
                }
                sendChunk(emitter, AgentApiConstants.MESSAGE_STREAM_AI_UNAVAILABLE);
                sendDone(emitter, null, null);
            }
        });
    }

    public String executeMultiTask(String userId, String sessionId, String task) {
        long startTime = System.currentTimeMillis();
        try {
            MultiAgentExecutionResult result = multiAgentTraceService.execute(
                    userId, sessionId, task, new MultiAgentExecutionListener() { });
            long latency = System.currentTimeMillis() - startTime;
            recordSuccessMetrics(
                    AgentApiConstants.AGENT_TYPE_MULTI, latency, result.getPromptTokens(), result.getCompletionTokens());
            log.info("[Multi] output userId={}, latency={}ms, responseLength={}, tokens={}/{}, response={}",
                    userId,
                    latency,
                    result.getContent() != null ? result.getContent().length() : 0,
                    result.getPromptTokens(),
                    result.getCompletionTokens(),
                    truncate(result.getContent(), 500));
            return result.getContent();
        } catch (RuntimeException e) {
            long latency = System.currentTimeMillis() - startTime;
            log.error("[Multi] failed userId={}, latency={}ms, error={}",
                    userId, latency, e.getMessage(), e);
            throw e;
        }
    }

    private void completeStreamSuccess(SseEmitter emitter,
                                       String agentType,
                                       String userId,
                                       String sessionId,
                                       String message,
                                       long startTime,
                                       long agentStartTime,
                                       AtomicInteger chunkCount,
                                       StringBuilder fullResponse,
                                       int promptTokens,
                                       int completionTokens,
                                       AgentExecutionMetrics executionMetrics) {
        metricsCollector.decrementActive();
        long latency = System.currentTimeMillis() - startTime;
        long persistenceStartTime = System.currentTimeMillis();
        recordSuccessMetrics(agentType, latency, promptTokens, completionTokens);
        String response = fullResponse.toString();
        log.info("[Stream] output agent={}, userId={}, latency={}ms, chunks={}, responseLength={}, promptTokens={}, completionTokens={}, response={}",
                agentType,
                userId,
                latency,
                chunkCount.get(),
                response.length(),
                promptTokens,
                completionTokens,
                truncate(response, 500));
        accessChecker.recordActualTokens(
                userId,
                agentType,
                promptTokens + completionTokens,
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
                promptTokens,
                completionTokens);
        sendDone(emitter, responseId, null);
    }

    private void failStreamExecution(SseEmitter emitter,
                                     String agentType,
                                     String userId,
                                     String sessionId,
                                     String message,
                                     long startTime,
                                     long agentStartTime,
                                     AtomicInteger chunkCount,
                                     StringBuilder fullResponse,
                                     AgentExecutionMetrics executionMetrics,
                                     Throwable error) {
        metricsCollector.decrementActive();
        long latency = System.currentTimeMillis() - startTime;
        long persistenceStartTime = System.currentTimeMillis();
        recordFailureMetrics(agentType, latency, false);
        log.error("[Stream] failed agent={}, userId={}, latency={}ms, chunks={}, partialResponse={}, error={}",
                agentType,
                userId,
                latency,
                chunkCount.get(),
                truncate(fullResponse.toString(), 200),
                error.getMessage(),
                error);
        accessChecker.recordActualTokens(userId, agentType, 0, AgentApiConstants.PRE_DEDUCT_TOKENS);
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
        recordSuccessMetrics(
                AgentApiConstants.AGENT_TYPE_MULTI,
                latency,
                multiResult.getPromptTokens(),
                multiResult.getCompletionTokens()
        );
        log.info("[Stream] multi-agent completed userId={}, latency={}ms, chunks={}, tokens={}/{}",
                userId, latency, chunkCount.get(), multiResult.getPromptTokens(), multiResult.getCompletionTokens());
        Long responseId = agentAuditLogService.saveAuditLog(
                userId,
                sessionId,
                AgentApiConstants.AGENT_TYPE_MULTI,
                message,
                truncate(multiResult.getContent(), 500),
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
                AgentApiConstants.AGENT_TYPE_MULTI,
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

    private void recordSuccessMetrics(String agentType, long latency, int promptTokens, int completionTokens) {
        metricsCollector.recordModelCall(agentType, latency, true);
        metricsCollector.recordRequest(null, agentType, latency, true, promptTokens, completionTokens);
    }

    private void recordFailureMetrics(String agentType, long latency, boolean includeMultiAlias) {
        if (includeMultiAlias) {
            metricsCollector.recordModelCall(AgentApiConstants.AGENT_TYPE_MULTI, latency, false);
            metricsCollector.recordRequest(null, AgentApiConstants.AGENT_TYPE_MULTI, latency, false, 0, 0);
        }
        metricsCollector.recordModelCall(agentType, latency, false);
        metricsCollector.recordRequest(null, agentType, latency, false, 0, 0);
    }

    private void handleChatFailure(String agentType, String userId, String sessionId, long startTime, boolean includeMultiAlias) {
        long latency = System.currentTimeMillis() - startTime;
        recordFailureMetrics(agentType, latency, includeMultiAlias);
        accessChecker.recordActualTokens(userId, agentType, 0, AgentApiConstants.PRE_DEDUCT_TOKENS);
        memoryService.rollbackLastUserMessage(sessionId);
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

    private String truncate(String text, int maxLen) {
        if (text == null) {
            return "null";
        }
        return text.length() <= maxLen
                ? text
                : text.substring(0, maxLen) + "...(truncated, total=" + text.length() + ")";
    }
}
