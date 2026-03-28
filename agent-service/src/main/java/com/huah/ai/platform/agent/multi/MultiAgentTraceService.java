package com.huah.ai.platform.agent.multi;

import com.huah.ai.platform.agent.dto.MultiAgentTraceResponse;
import com.huah.ai.platform.agent.dto.MultiAgentTraceStepResponse;
import com.huah.ai.platform.agent.memory.ConversationMemoryService;
import com.huah.ai.platform.common.trace.TraceIdContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MultiAgentTraceService {

    private static final int MAX_SUMMARY_LENGTH = 1000;
    private static final String ACTION_RETRY = "retry";
    private static final String ACTION_REPLAY = "replay";
    private static final String ACTION_SKIP = "skip";

    private final MultiAgentOrchestrator multiAgentOrchestrator;
    private final MultiAgentExecutionTraceMapper traceMapper;
    private final MultiAgentExecutionStepMapper stepMapper;
    private final ConversationMemoryService memoryService;

    public MultiAgentExecutionResult execute(String userId,
                                             String sessionId,
                                             String task,
                                             MultiAgentExecutionListener listener) {
        return executeFreshTrace(userId, sessionId, task, listener, null, null);
    }

    public MultiAgentTraceResponse replayArchivedTrace(String userId,
                                                       String sourceTraceId,
                                                       String task,
                                                       MultiAgentExecutionListener listener) {
        String replayTask = defaultString(task).trim();
        if (replayTask.isEmpty()) {
            throw new IllegalArgumentException("Archived trace does not contain a replayable task");
        }
        String replaySessionId = "archived-replay-" + sourceTraceId + "-" + System.currentTimeMillis();
        MultiAgentExecutionResult result = executeFreshTrace(
                userId,
                replaySessionId,
                replayTask,
                listener == null ? new MultiAgentExecutionListener() { } : listener,
                sourceTraceId,
                "archive-replay"
        );
        return getTrace(userId, result.getTraceId());
    }

    public MultiAgentTraceResponse recoverTrace(String userId,
                                                String sourceTraceId,
                                                Integer stepOrder,
                                                String action,
                                                MultiAgentExecutionListener listener) {
        MultiAgentExecutionTrace sourceTrace = traceMapper.selectByTraceIdAndUserId(sourceTraceId, userId);
        if (sourceTrace == null) {
            throw new IllegalArgumentException("未找到可恢复的多智能体轨迹");
        }
        List<MultiAgentExecutionStep> sourceSteps = stepMapper.selectByTraceId(sourceTraceId);
        if (sourceSteps.isEmpty()) {
            throw new IllegalArgumentException("当前轨迹没有可恢复的步骤");
        }
        int effectiveStepOrder = stepOrder == null ? sourceSteps.get(sourceSteps.size() - 1).getStepOrder() : stepOrder;
        MultiAgentExecutionStep targetStep = sourceSteps.stream()
                .filter(step -> Objects.equals(step.getStepOrder(), effectiveStepOrder))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未找到指定步骤"));

        String normalizedAction = normalizeAction(action);
        if (!Boolean.TRUE.equals(targetStep.getRecoverable())) {
            throw new IllegalArgumentException("当前步骤不支持恢复操作");
        }

        RecoveryExecutionResult recovery = executeRecoveryTrace(
                userId,
                sourceTrace,
                sourceSteps,
                targetStep,
                normalizedAction,
                listener == null ? new MultiAgentExecutionListener() { } : listener
        );
        return toResponse(recovery.trace(), recovery.steps());
    }

    public MultiAgentTraceResponse getTrace(String userId, String traceId) {
        MultiAgentExecutionTrace trace = traceMapper.selectByTraceIdAndUserId(traceId, userId);
        if (trace == null) {
            return null;
        }
        return toResponse(trace, stepMapper.selectByTraceId(traceId));
    }

    public List<MultiAgentTraceResponse> listTraces(String userId, String sessionId, int limit) {
        return traceMapper.selectRecentByUser(userId, sessionId, limit).stream()
                .map(trace -> toResponse(trace, null))
                .toList();
    }

    private MultiAgentExecutionResult executeFreshTrace(String userId,
                                                        String sessionId,
                                                        String task,
                                                        MultiAgentExecutionListener listener,
                                                        String parentTraceId,
                                                        String recoveryAction) {
        long startedAt = System.currentTimeMillis();
        String traceId = currentTraceId();
        String internalId = sessionId + "-multi-" + System.currentTimeMillis();
        List<MultiAgentExecutionStep> steps = new ArrayList<>();

        MultiAgentOrchestrator.StepResult planResult = null;
        MultiAgentOrchestrator.StepResult executeResult = null;
        MultiAgentOrchestrator.StepResult criticResult = null;
        try {
            planResult = runPlanner(traceId, task, internalId, listener);
            steps.add(toStep(traceId, 1, "planner", "Planner", summarize(task), planResult, true, null, planResult.getLatencyMs()));

            String executionInput = summarize("任务:\n" + task + "\n\n规划:\n" + planResult.getContent());
            executeResult = runExecutor(traceId, userId, task, planResult.getContent(), internalId, executionInput, listener);
            steps.add(toStep(traceId, 2, "executor", "Executor", executionInput, executeResult, true, null, executeResult.getLatencyMs()));

            String criticInput = summarize("原始任务:\n" + task + "\n\n执行结果:\n" + executeResult.getContent());
            criticResult = runCritic(traceId, task, executeResult.getContent(), internalId, criticInput, listener);
            steps.add(toStep(traceId, 3, "critic", "Critic", criticInput, criticResult, true, null, criticResult.getLatencyMs()));

            long totalLatency = System.currentTimeMillis() - startedAt;
            memoryService.saveExchange(sessionId, task, criticResult.getContent());
            persistTrace(buildTraceRecord(
                    traceId, userId, sessionId, task, criticResult.getContent(), "SUCCESS",
                    sumPromptTokens(planResult, executeResult, criticResult),
                    sumCompletionTokens(planResult, executeResult, criticResult),
                    totalLatency, steps, null, parentTraceId, parentTraceId, null, recoveryAction
            ), steps);

            return MultiAgentExecutionResult.builder()
                    .traceId(traceId)
                    .sessionId(sessionId)
                    .userId(userId)
                    .content(criticResult.getContent())
                    .promptTokens(sumPromptTokens(planResult, executeResult, criticResult))
                    .completionTokens(sumCompletionTokens(planResult, executeResult, criticResult))
                    .latencyMs(totalLatency)
                    .success(true)
                    .steps(steps)
                    .build();
        } catch (StageExecutionException ex) {
            steps.add(ex.getFailedStep());
            long totalLatency = System.currentTimeMillis() - startedAt;
            String errorMessage = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
            listener.onFailed(ex.getFailedStep().getStage(), errorMessage);
            persistTrace(buildTraceRecord(
                    traceId, userId, sessionId, task,
                    criticResult != null ? criticResult.getContent() : executeResult != null ? executeResult.getContent() : null,
                    "FAILED",
                    sumPromptTokens(planResult, executeResult, criticResult),
                    sumCompletionTokens(planResult, executeResult, criticResult),
                    totalLatency, steps, errorMessage, parentTraceId, parentTraceId, null, recoveryAction
            ), steps);
            throw ex;
        } catch (Exception ex) {
            long totalLatency = System.currentTimeMillis() - startedAt;
            String errorMessage = ex.getMessage();
            listener.onFailed(null, errorMessage);
            persistTrace(buildTraceRecord(
                    traceId, userId, sessionId, task,
                    criticResult != null ? criticResult.getContent() : executeResult != null ? executeResult.getContent() : null,
                    "FAILED",
                    sumPromptTokens(planResult, executeResult, criticResult),
                    sumCompletionTokens(planResult, executeResult, criticResult),
                    totalLatency, steps, errorMessage, parentTraceId, parentTraceId, null, recoveryAction
            ), steps);
            throw ex;
        }
    }

    private RecoveryExecutionResult executeRecoveryTrace(String userId,
                                                         MultiAgentExecutionTrace sourceTrace,
                                                         List<MultiAgentExecutionStep> sourceSteps,
                                                         MultiAgentExecutionStep targetStep,
                                                         String action,
                                                         MultiAgentExecutionListener listener) {
        long startedAt = System.currentTimeMillis();
        String recoveryTraceId = UUID.randomUUID().toString();
        String internalId = sourceTrace.getSessionId() + "-recovery-" + System.currentTimeMillis();
        List<MultiAgentExecutionStep> recoverySteps = new ArrayList<>();
        Map<String, MultiAgentExecutionStep> sourceByStage = toStageMap(sourceSteps);

        String task = defaultString(sourceTrace.getRequestSummary());
        String planContent = sourceOutput(sourceByStage.get("planner"));
        String executeContent = sourceOutput(sourceByStage.get("executor"));
        String finalContent = sourceTrace.getFinalSummary();

        try {
            for (MultiAgentExecutionStep sourceStep : sourceSteps) {
                if (sourceStep.getStepOrder() < targetStep.getStepOrder()) {
                    recoverySteps.add(copyStepForRecovery(recoveryTraceId, sourceStep, "reuse"));
                    continue;
                }

                if (sourceStep.getStepOrder().equals(targetStep.getStepOrder()) && ACTION_SKIP.equals(action)) {
                    recoverySteps.add(MultiAgentExecutionStep.builder()
                            .id(UUID.randomUUID().toString())
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
                            .sourceTraceId(sourceTrace.getTraceId())
                            .sourceStepOrder(sourceStep.getStepOrder())
                            .createdAt(LocalDateTime.now())
                            .build());
                    continue;
                }

                if ("planner".equals(sourceStep.getStage())) {
                    MultiAgentOrchestrator.StepResult planResult = runPlanner(recoveryTraceId, task, internalId, listener);
                    recoverySteps.add(toRecoveryStep(recoveryTraceId, sourceTrace.getTraceId(), sourceStep.getStepOrder(), action,
                            "planner", "Planner", summarize(task), planResult, true, null));
                    planContent = planResult.getContent();
                    continue;
                }

                if ("executor".equals(sourceStep.getStage())) {
                    String executionInput = summarize("任务:\n" + task + "\n\n规划:\n" + defaultString(planContent));
                    MultiAgentOrchestrator.StepResult executeResult = runExecutor(
                            recoveryTraceId, userId, task, planContent, internalId, executionInput, listener);
                    recoverySteps.add(toRecoveryStep(recoveryTraceId, sourceTrace.getTraceId(), sourceStep.getStepOrder(), action,
                            "executor", "Executor", executionInput, executeResult, true, null));
                    executeContent = executeResult.getContent();
                    continue;
                }

                if ("critic".equals(sourceStep.getStage())) {
                    String criticInput = summarize("原始任务:\n" + task + "\n\n执行结果:\n" + defaultString(executeContent));
                    MultiAgentOrchestrator.StepResult criticResult = runCritic(
                            recoveryTraceId, task, executeContent, internalId, criticInput, listener);
                    recoverySteps.add(toRecoveryStep(recoveryTraceId, sourceTrace.getTraceId(), sourceStep.getStepOrder(), action,
                            "critic", "Critic", criticInput, criticResult, true, null));
                    finalContent = criticResult.getContent();
                }
            }

            long totalLatency = System.currentTimeMillis() - startedAt;
            MultiAgentExecutionTrace recoveryTrace = buildTraceRecord(
                    recoveryTraceId, userId, sourceTrace.getSessionId(), task, finalContent, "RECOVERED_SUCCESS",
                    recoverySteps.stream().mapToInt(step -> step.getPromptTokens() == null ? 0 : step.getPromptTokens()).sum(),
                    recoverySteps.stream().mapToInt(step -> step.getCompletionTokens() == null ? 0 : step.getCompletionTokens()).sum(),
                    totalLatency, recoverySteps, null, sourceTrace.getTraceId(), sourceTrace.getTraceId(),
                    targetStep.getStepOrder(), action
            );
            persistTrace(recoveryTrace, recoverySteps);
            return new RecoveryExecutionResult(recoveryTrace, recoverySteps);
        } catch (StageExecutionException ex) {
            recoverySteps.add(markFailedRecoveryStep(recoveryTraceId, sourceTrace.getTraceId(), action, ex.getFailedStep()));
            long totalLatency = System.currentTimeMillis() - startedAt;
            persistTrace(buildTraceRecord(
                    recoveryTraceId, userId, sourceTrace.getSessionId(), task, finalContent, "RECOVERED_FAILED",
                    recoverySteps.stream().mapToInt(step -> step.getPromptTokens() == null ? 0 : step.getPromptTokens()).sum(),
                    recoverySteps.stream().mapToInt(step -> step.getCompletionTokens() == null ? 0 : step.getCompletionTokens()).sum(),
                    totalLatency, recoverySteps, ex.getMessage(), sourceTrace.getTraceId(), sourceTrace.getTraceId(),
                    targetStep.getStepOrder(), action
            ), recoverySteps);
            throw ex;
        }
    }

    private MultiAgentOrchestrator.StepResult runPlanner(String traceId, String task, String internalId, MultiAgentExecutionListener listener) {
        return runStage(traceId, 1, "planner", "Planner", summarize(task), listener,
                () -> multiAgentOrchestrator.planTask(task, internalId + "-planner"));
    }

    private MultiAgentOrchestrator.StepResult runExecutor(String traceId,
                                                          String userId,
                                                          String task,
                                                          String planContent,
                                                          String internalId,
                                                          String executionInput,
                                                          MultiAgentExecutionListener listener) {
        return runStage(traceId, 2, "executor", "Executor", executionInput, listener,
                () -> multiAgentOrchestrator.executeWithTools(userId, task, defaultString(planContent), internalId + "-executor"));
    }

    private MultiAgentOrchestrator.StepResult runCritic(String traceId,
                                                        String task,
                                                        String executionContent,
                                                        String internalId,
                                                        String criticInput,
                                                        MultiAgentExecutionListener listener) {
        return runStage(traceId, 3, "critic", "Critic", criticInput, listener,
                () -> multiAgentOrchestrator.critique(task, defaultString(executionContent), internalId + "-critic"));
    }

    private MultiAgentOrchestrator.StepResult runStage(String traceId,
                                                       int order,
                                                       String stage,
                                                       String label,
                                                       String inputSummary,
                                                       MultiAgentExecutionListener listener,
                                                       StageSupplier supplier) {
        listener.onStageStarted(stage, label);
        long start = System.currentTimeMillis();
        try {
            MultiAgentOrchestrator.StepResult result = supplier.get().withLatencyMs(System.currentTimeMillis() - start);
            listener.onStageCompleted(toStep(traceId, order, stage, label, inputSummary, result, true, null, result.getLatencyMs()));
            return result;
        } catch (Exception ex) {
            long latency = System.currentTimeMillis() - start;
            MultiAgentExecutionStep failedStep = MultiAgentExecutionStep.builder()
                    .traceId(traceId)
                    .stepOrder(order)
                    .stage(stage)
                    .agentName(label)
                    .inputSummary(inputSummary)
                    .outputSummary(null)
                    .promptTokens(0)
                    .completionTokens(0)
                    .latencyMs(latency)
                    .success(false)
                    .errorMessage(summarize(ex.getMessage()))
                    .recoverable(true)
                    .skipped(false)
                    .createdAt(LocalDateTime.now())
                    .build();
            listener.onStageCompleted(failedStep);
            throw new StageExecutionException(failedStep, ex);
        }
    }

    private void persistTrace(MultiAgentExecutionTrace trace, List<MultiAgentExecutionStep> steps) {
        try {
            traceMapper.insert(trace);
            for (MultiAgentExecutionStep step : steps) {
                stepMapper.insert(step);
            }
        } catch (Exception ex) {
            log.warn("persist multi-agent trace failed, traceId={}, error={}", trace.getTraceId(), ex.getMessage());
        }
    }

    private MultiAgentExecutionTrace buildTraceRecord(String traceId,
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
                .id(UUID.randomUUID().toString())
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

    private MultiAgentExecutionStep toStep(String traceId,
                                           int order,
                                           String stage,
                                           String agentName,
                                           String inputSummary,
                                           MultiAgentOrchestrator.StepResult result,
                                           boolean success,
                                           String errorMessage,
                                           long latencyMs) {
        return MultiAgentExecutionStep.builder()
                .id(UUID.randomUUID().toString())
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

    private MultiAgentExecutionStep toRecoveryStep(String traceId,
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
                .id(UUID.randomUUID().toString())
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

    private MultiAgentExecutionStep copyStepForRecovery(String recoveryTraceId, MultiAgentExecutionStep sourceStep, String action) {
        return MultiAgentExecutionStep.builder()
                .id(UUID.randomUUID().toString())
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

    private MultiAgentExecutionStep markFailedRecoveryStep(String recoveryTraceId,
                                                           String sourceTraceId,
                                                           String action,
                                                           MultiAgentExecutionStep failedStep) {
        return MultiAgentExecutionStep.builder()
                .id(UUID.randomUUID().toString())
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

    private MultiAgentTraceResponse toResponse(MultiAgentExecutionTrace trace, List<MultiAgentExecutionStep> steps) {
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

    private int sumPromptTokens(MultiAgentOrchestrator.StepResult... results) {
        int total = 0;
        for (MultiAgentOrchestrator.StepResult result : results) {
            if (result != null) {
                total += result.getPromptTokens();
            }
        }
        return total;
    }

    private int sumCompletionTokens(MultiAgentOrchestrator.StepResult... results) {
        int total = 0;
        for (MultiAgentOrchestrator.StepResult result : results) {
            if (result != null) {
                total += result.getCompletionTokens();
            }
        }
        return total;
    }

    private String currentTraceId() {
        String traceId = TraceIdContext.currentTraceId();
        return traceId == null || traceId.isBlank() ? UUID.randomUUID().toString() : traceId;
    }

    private String summarize(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= MAX_SUMMARY_LENGTH ? text : text.substring(0, MAX_SUMMARY_LENGTH) + "...";
    }

    private String normalizeAction(String action) {
        if (action == null || action.isBlank()) {
            return ACTION_RETRY;
        }
        String normalized = action.trim().toLowerCase();
        if (!List.of(ACTION_RETRY, ACTION_REPLAY, ACTION_SKIP).contains(normalized)) {
            throw new IllegalArgumentException("不支持的恢复动作: " + action);
        }
        return normalized;
    }

    private Map<String, MultiAgentExecutionStep> toStageMap(List<MultiAgentExecutionStep> steps) {
        Map<String, MultiAgentExecutionStep> mapping = new LinkedHashMap<>();
        for (MultiAgentExecutionStep step : steps) {
            mapping.put(step.getStage(), step);
        }
        return mapping;
    }

    private String sourceOutput(MultiAgentExecutionStep step) {
        return step == null ? "" : defaultString(step.getOutputSummary());
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    @FunctionalInterface
    private interface StageSupplier {
        MultiAgentOrchestrator.StepResult get();
    }

    private record RecoveryExecutionResult(MultiAgentExecutionTrace trace, List<MultiAgentExecutionStep> steps) {
    }

    private static class StageExecutionException extends RuntimeException {
        private final MultiAgentExecutionStep failedStep;

        private StageExecutionException(MultiAgentExecutionStep failedStep, Throwable cause) {
            super(cause);
            this.failedStep = failedStep;
        }

        public MultiAgentExecutionStep getFailedStep() {
            return failedStep;
        }
    }
}
