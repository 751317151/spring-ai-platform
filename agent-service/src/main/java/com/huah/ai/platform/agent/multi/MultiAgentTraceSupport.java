package com.huah.ai.platform.agent.multi;

import com.huah.ai.platform.agent.dto.MultiAgentTraceResponse;
import com.huah.ai.platform.agent.dto.MultiAgentTraceStepResponse;
import com.huah.ai.platform.common.trace.TraceIdContext;
import com.huah.ai.platform.common.util.SnowflakeIdGenerator;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class MultiAgentTraceSupport {

    static final int MAX_SUMMARY_LENGTH = 1000;
    static final String INTERNAL_SESSION_PREFIX = "m";
    static final String RECOVERY_SESSION_PREFIX = "r";
    static final String ACTION_RETRY = "retry";
    static final String ACTION_REPLAY = "replay";
    static final String ACTION_SKIP = "skip";

    private final SnowflakeIdGenerator snowflakeIdGenerator;

    MultiAgentTraceSupport(SnowflakeIdGenerator snowflakeIdGenerator) {
        this.snowflakeIdGenerator = snowflakeIdGenerator;
    }

    MultiAgentExecutionTrace buildTraceRecord(String traceId,
                                              String userId,
                                              String sessionId,
                                              String task,
                                              String finalSummary,
                                              String status,
                                              int promptTokens,
                                              int completionTokens,
                                              long latencyMs,
                                              List<MultiAgentExecutionStep> steps,
                                              String errorMessage,
                                              String parentTraceId,
                                              String recoverySourceTraceId,
                                              Integer recoverySourceStepOrder,
                                              String recoveryAction) {
        LocalDateTime now = LocalDateTime.now();
        return MultiAgentExecutionTrace.builder()
                .id(snowflakeIdGenerator.nextLongId())
                .traceId(traceId)
                .userId(userId)
                .sessionId(sessionId)
                .agentType("multi")
                .requestSummary(summarize(task))
                .finalSummary(summarize(finalSummary))
                .status(status)
                .totalPromptTokens(promptTokens)
                .totalCompletionTokens(completionTokens)
                .totalLatencyMs(latencyMs)
                .stepCount(steps.size())
                .errorMessage(summarize(errorMessage))
                .parentTraceId(parentTraceId)
                .recoverySourceTraceId(recoverySourceTraceId)
                .recoverySourceStepOrder(recoverySourceStepOrder)
                .recoveryAction(recoveryAction)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    MultiAgentExecutionStep toStep(String traceId,
                                   int order,
                                   String stage,
                                   String agentName,
                                   String inputSummary,
                                   MultiAgentOrchestrator.StepResult result,
                                   boolean success,
                                   String errorMessage,
                                   long latencyMs) {
        return MultiAgentExecutionStep.builder()
                .id(snowflakeIdGenerator.nextLongId())
                .traceId(traceId)
                .stepOrder(order)
                .stage(stage)
                .agentName(agentName)
                .inputSummary(inputSummary)
                .outputSummary(summarize(result != null ? result.getContent() : null))
                .promptTokens(result != null ? result.getPromptTokens() : 0)
                .completionTokens(result != null ? result.getCompletionTokens() : 0)
                .latencyMs(latencyMs)
                .success(success)
                .errorMessage(summarize(errorMessage))
                .recoverable(true)
                .skipped(false)
                .createdAt(LocalDateTime.now())
                .build();
    }

    MultiAgentExecutionStep buildSkippedRecoveryStep(String recoveryTraceId,
                                                     MultiAgentExecutionStep sourceStep,
                                                     String action) {
        return MultiAgentExecutionStep.builder()
                .id(snowflakeIdGenerator.nextLongId())
                .traceId(recoveryTraceId)
                .stepOrder(sourceStep.getStepOrder())
                .stage(sourceStep.getStage())
                .agentName(sourceStep.getAgentName())
                .inputSummary(sourceStep.getInputSummary())
                .outputSummary(sourceOutput(sourceStep))
                .promptTokens(0)
                .completionTokens(0)
                .latencyMs(0L)
                .success(true)
                .errorMessage("步骤已跳过，沿用已有上下文继续执行")
                .recoverable(true)
                .skipped(true)
                .recoveryAction(action)
                .sourceTraceId(sourceStep.getTraceId())
                .sourceStepOrder(sourceStep.getStepOrder())
                .createdAt(LocalDateTime.now())
                .build();
    }

    MultiAgentExecutionStep toRecoveryStep(String traceId,
                                           String sourceTraceId,
                                           Integer sourceStepOrder,
                                           String action,
                                           String stage,
                                           String agentName,
                                           String inputSummary,
                                           MultiAgentOrchestrator.StepResult result,
                                           boolean success,
                                           String errorMessage) {
        return MultiAgentExecutionStep.builder()
                .id(snowflakeIdGenerator.nextLongId())
                .traceId(traceId)
                .stepOrder(sourceStepOrder)
                .stage(stage)
                .agentName(agentName)
                .inputSummary(inputSummary)
                .outputSummary(summarize(result != null ? result.getContent() : null))
                .promptTokens(result != null ? result.getPromptTokens() : 0)
                .completionTokens(result != null ? result.getCompletionTokens() : 0)
                .latencyMs(result != null ? result.getLatencyMs() : 0L)
                .success(success)
                .errorMessage(summarize(errorMessage))
                .recoverable(true)
                .skipped(false)
                .recoveryAction(action)
                .sourceTraceId(sourceTraceId)
                .sourceStepOrder(sourceStepOrder)
                .createdAt(LocalDateTime.now())
                .build();
    }

    MultiAgentExecutionStep copyStepForRecovery(String recoveryTraceId, MultiAgentExecutionStep sourceStep, String action) {
        return MultiAgentExecutionStep.builder()
                .id(snowflakeIdGenerator.nextLongId())
                .traceId(recoveryTraceId)
                .stepOrder(sourceStep.getStepOrder())
                .stage(sourceStep.getStage())
                .agentName(sourceStep.getAgentName())
                .inputSummary(sourceStep.getInputSummary())
                .outputSummary(sourceStep.getOutputSummary())
                .promptTokens(sourceStep.getPromptTokens())
                .completionTokens(sourceStep.getCompletionTokens())
                .latencyMs(sourceStep.getLatencyMs())
                .success(sourceStep.getSuccess())
                .errorMessage(sourceStep.getErrorMessage())
                .recoverable(sourceStep.getRecoverable())
                .skipped(Boolean.TRUE.equals(sourceStep.getSkipped()))
                .recoveryAction(action)
                .sourceTraceId(sourceStep.getTraceId())
                .sourceStepOrder(sourceStep.getStepOrder())
                .createdAt(LocalDateTime.now())
                .build();
    }

    MultiAgentExecutionStep markFailedRecoveryStep(String recoveryTraceId,
                                                   String sourceTraceId,
                                                   String action,
                                                   MultiAgentExecutionStep failedStep) {
        return MultiAgentExecutionStep.builder()
                .id(snowflakeIdGenerator.nextLongId())
                .traceId(recoveryTraceId)
                .stepOrder(failedStep.getStepOrder())
                .stage(failedStep.getStage())
                .agentName(failedStep.getAgentName())
                .inputSummary(failedStep.getInputSummary())
                .outputSummary(failedStep.getOutputSummary())
                .promptTokens(failedStep.getPromptTokens())
                .completionTokens(failedStep.getCompletionTokens())
                .latencyMs(failedStep.getLatencyMs())
                .success(false)
                .errorMessage(failedStep.getErrorMessage())
                .recoverable(true)
                .skipped(false)
                .recoveryAction(action)
                .sourceTraceId(sourceTraceId)
                .sourceStepOrder(failedStep.getStepOrder())
                .createdAt(LocalDateTime.now())
                .build();
    }

    MultiAgentTraceResponse toResponse(MultiAgentExecutionTrace trace, List<MultiAgentExecutionStep> steps) {
        return MultiAgentTraceResponse.builder()
                .traceId(trace.getTraceId())
                .sessionId(trace.getSessionId())
                .userId(trace.getUserId())
                .agentType(trace.getAgentType())
                .requestSummary(trace.getRequestSummary())
                .finalSummary(trace.getFinalSummary())
                .status(trace.getStatus())
                .totalPromptTokens(trace.getTotalPromptTokens())
                .totalCompletionTokens(trace.getTotalCompletionTokens())
                .totalLatencyMs(trace.getTotalLatencyMs())
                .stepCount(trace.getStepCount())
                .errorMessage(trace.getErrorMessage())
                .parentTraceId(trace.getParentTraceId())
                .recoverySourceTraceId(trace.getRecoverySourceTraceId())
                .recoverySourceStepOrder(trace.getRecoverySourceStepOrder())
                .recoveryAction(trace.getRecoveryAction())
                .createdAt(trace.getCreatedAt())
                .updatedAt(trace.getUpdatedAt())
                .steps(steps == null ? null : steps.stream().map(this::toStepResponse).toList())
                .build();
    }

    int sumPromptTokens(MultiAgentOrchestrator.StepResult... results) {
        int total = 0;
        for (MultiAgentOrchestrator.StepResult result : results) {
            if (result != null) {
                total += result.getPromptTokens();
            }
        }
        return total;
    }

    int sumCompletionTokens(MultiAgentOrchestrator.StepResult... results) {
        int total = 0;
        for (MultiAgentOrchestrator.StepResult result : results) {
            if (result != null) {
                total += result.getCompletionTokens();
            }
        }
        return total;
    }

    String currentTraceId() {
        String traceId = TraceIdContext.currentTraceId();
        return traceId == null || traceId.isBlank() ? UUID.randomUUID().toString() : traceId;
    }

    String summarize(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= MAX_SUMMARY_LENGTH ? text : text.substring(0, MAX_SUMMARY_LENGTH) + "...";
    }

    String normalizeAction(String action) {
        if (action == null || action.isBlank()) {
            return ACTION_RETRY;
        }
        String normalized = action.trim().toLowerCase();
        if (!List.of(ACTION_RETRY, ACTION_REPLAY, ACTION_SKIP).contains(normalized)) {
            throw new IllegalArgumentException("涓嶆敮鎸佺殑鎭㈠鍔ㄤ綔: " + action);
        }
        return normalized;
    }

    Map<String, MultiAgentExecutionStep> toStageMap(List<MultiAgentExecutionStep> steps) {
        Map<String, MultiAgentExecutionStep> mapping = new LinkedHashMap<>();
        for (MultiAgentExecutionStep step : steps) {
            mapping.put(step.getStage(), step);
        }
        return mapping;
    }

    String sourceOutput(MultiAgentExecutionStep step) {
        return step == null ? "" : defaultString(step.getOutputSummary());
    }

    String defaultString(String value) {
        return value == null ? "" : value;
    }

    String nextInternalSessionId(String prefix) {
        return prefix + snowflakeIdGenerator.nextLongId();
    }

    private MultiAgentTraceStepResponse toStepResponse(MultiAgentExecutionStep step) {
        return MultiAgentTraceStepResponse.builder()
                .stepOrder(step.getStepOrder())
                .stage(step.getStage())
                .agentName(step.getAgentName())
                .inputSummary(step.getInputSummary())
                .outputSummary(step.getOutputSummary())
                .promptTokens(step.getPromptTokens())
                .completionTokens(step.getCompletionTokens())
                .latencyMs(step.getLatencyMs())
                .success(step.getSuccess())
                .errorMessage(step.getErrorMessage())
                .recoverable(step.getRecoverable())
                .skipped(step.getSkipped())
                .recoveryAction(step.getRecoveryAction())
                .sourceTraceId(step.getSourceTraceId())
                .sourceStepOrder(step.getSourceStepOrder())
                .createdAt(step.getCreatedAt())
                .build();
    }
}
