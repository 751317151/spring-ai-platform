package com.huah.ai.platform.agent.multi;

import com.huah.ai.platform.agent.dto.MultiAgentTraceResponse;
import com.huah.ai.platform.agent.memory.ConversationMemoryService;
import com.huah.ai.platform.common.util.SnowflakeIdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
public class MultiAgentTraceService {

    private final MultiAgentExecutionTraceMapper traceMapper;
    private final MultiAgentExecutionStepMapper stepMapper;
    private final ConversationMemoryService memoryService;
    private final MultiAgentTraceSupport traceSupport;
    private final MultiAgentStageRunner stageRunner;

    public MultiAgentTraceService(MultiAgentOrchestrator multiAgentOrchestrator,
                                  MultiAgentExecutionTraceMapper traceMapper,
                                  MultiAgentExecutionStepMapper stepMapper,
                                  ConversationMemoryService memoryService,
                                  SnowflakeIdGenerator snowflakeIdGenerator) {
        this.traceMapper = traceMapper;
        this.stepMapper = stepMapper;
        this.memoryService = memoryService;
        this.traceSupport = new MultiAgentTraceSupport(snowflakeIdGenerator);
        this.stageRunner = new MultiAgentStageRunner(multiAgentOrchestrator, traceSupport);
    }

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
            throw new IllegalArgumentException("鏈壘鍒板彲鎭㈠鐨勫鏅鸿兘浣撹建杩?");
        }
        List<MultiAgentExecutionStep> sourceSteps = stepMapper.selectByTraceId(sourceTraceId);
        if (sourceSteps.isEmpty()) {
            throw new IllegalArgumentException("当前轨迹没有可恢复的步骤");
        }
        int effectiveStepOrder = stepOrder == null ? sourceSteps.get(sourceSteps.size() - 1).getStepOrder() : stepOrder;
        MultiAgentExecutionStep targetStep = sourceSteps.stream()
                .filter(step -> Objects.equals(step.getStepOrder(), effectiveStepOrder))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未找到指定的恢复步骤"));

        String normalizedAction = normalizeAction(action);
        if (!Boolean.TRUE.equals(targetStep.getRecoverable())) {
            throw new IllegalArgumentException("涓嶆敮鎸佹仮澶?");
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
        String internalId = nextInternalSessionId(MultiAgentTraceSupport.INTERNAL_SESSION_PREFIX);
        List<MultiAgentExecutionStep> steps = new ArrayList<>();

        MultiAgentOrchestrator.StepResult planResult = null;
        MultiAgentOrchestrator.StepResult executeResult = null;
        MultiAgentOrchestrator.StepResult criticResult = null;
        try {
            planResult = stageRunner.runPlanner(traceId, task, internalId, listener);
            steps.add(toStep(traceId, 1, "planner", "Planner", summarize(task), planResult, true, null, planResult.getLatencyMs()));

            String executionInput = summarize("浠诲姟:\n" + task + "\n\n瑙勫垝:\n" + planResult.getContent());
            executeResult = stageRunner.runExecutor(traceId, userId, task, planResult.getContent(), internalId, executionInput, listener);
            steps.add(toStep(traceId, 2, "executor", "Executor", executionInput, executeResult, true, null, executeResult.getLatencyMs()));

            String criticInput = summarize("原始任务:\n" + task + "\n\n执行结果:\n" + executeResult.getContent());
            criticResult = stageRunner.runCritic(traceId, task, executeResult.getContent(), internalId, criticInput, listener);
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
        } catch (MultiAgentStageRunner.StageExecutionException ex) {
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
        } catch (RuntimeException ex) {
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
        String internalId = nextInternalSessionId(MultiAgentTraceSupport.RECOVERY_SESSION_PREFIX);
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

                if (sourceStep.getStepOrder().equals(targetStep.getStepOrder())
                        && MultiAgentTraceSupport.ACTION_SKIP.equals(action)) {
                    recoverySteps.add(traceSupport.buildSkippedRecoveryStep(recoveryTraceId, sourceStep, action));
                    continue;
                }

                if ("planner".equals(sourceStep.getStage())) {
                    MultiAgentOrchestrator.StepResult planResult =
                            stageRunner.runPlanner(recoveryTraceId, task, internalId, listener);
                    recoverySteps.add(toRecoveryStep(recoveryTraceId, sourceTrace.getTraceId(), sourceStep.getStepOrder(), action,
                            "planner", "Planner", summarize(task), planResult, true, null));
                    planContent = planResult.getContent();
                    continue;
                }

                if ("executor".equals(sourceStep.getStage())) {
                    String executionInput = summarize("浠诲姟:\n" + task + "\n\n瑙勫垝:\n" + defaultString(planContent));
                    MultiAgentOrchestrator.StepResult executeResult = stageRunner.runExecutor(
                            recoveryTraceId, userId, task, planContent, internalId, executionInput, listener);
                    recoverySteps.add(toRecoveryStep(recoveryTraceId, sourceTrace.getTraceId(), sourceStep.getStepOrder(), action,
                            "executor", "Executor", executionInput, executeResult, true, null));
                    executeContent = executeResult.getContent();
                    continue;
                }

                if ("critic".equals(sourceStep.getStage())) {
                    String criticInput = summarize("原始任务:\n" + task + "\n\n执行结果:\n" + defaultString(executeContent));
                    MultiAgentOrchestrator.StepResult criticResult = stageRunner.runCritic(
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
        } catch (MultiAgentStageRunner.StageExecutionException ex) {
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

    private void persistTrace(MultiAgentExecutionTrace trace, List<MultiAgentExecutionStep> steps) {
        try {
            traceMapper.insert(trace);
            for (MultiAgentExecutionStep step : steps) {
                stepMapper.insert(step);
            }
        } catch (RuntimeException ex) {
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
        return traceSupport.buildTraceRecord(traceId, userId, sessionId, task, finalSummary, status, promptTokens,
                completionTokens, latencyMs, steps, errorMessage, parentTraceId, recoverySourceTraceId,
                recoverySourceStepOrder, recoveryAction);
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
        return traceSupport.toStep(traceId, order, stage, agentName, inputSummary, result, success, errorMessage, latencyMs);
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
        return traceSupport.toRecoveryStep(traceId, sourceTraceId, sourceStepOrder, action, stage, agentName, inputSummary,
                result, success, errorMessage);
    }

    private MultiAgentExecutionStep copyStepForRecovery(String recoveryTraceId,
                                                        MultiAgentExecutionStep sourceStep,
                                                        String action) {
        return traceSupport.copyStepForRecovery(recoveryTraceId, sourceStep, action);
    }

    private MultiAgentExecutionStep markFailedRecoveryStep(String recoveryTraceId,
                                                           String sourceTraceId,
                                                           String action,
                                                           MultiAgentExecutionStep failedStep) {
        return traceSupport.markFailedRecoveryStep(recoveryTraceId, sourceTraceId, action, failedStep);
    }

    private MultiAgentTraceResponse toResponse(MultiAgentExecutionTrace trace, List<MultiAgentExecutionStep> steps) {
        return traceSupport.toResponse(trace, steps);
    }

    private int sumPromptTokens(MultiAgentOrchestrator.StepResult... results) {
        return traceSupport.sumPromptTokens(results);
    }

    private int sumCompletionTokens(MultiAgentOrchestrator.StepResult... results) {
        return traceSupport.sumCompletionTokens(results);
    }

    private String currentTraceId() {
        return traceSupport.currentTraceId();
    }

    private String summarize(String text) {
        return traceSupport.summarize(text);
    }

    private String normalizeAction(String action) {
        return traceSupport.normalizeAction(action);
    }

    private Map<String, MultiAgentExecutionStep> toStageMap(List<MultiAgentExecutionStep> steps) {
        return traceSupport.toStageMap(steps);
    }

    private String sourceOutput(MultiAgentExecutionStep step) {
        return traceSupport.sourceOutput(step);
    }

    private String defaultString(String value) {
        return traceSupport.defaultString(value);
    }

    private String nextInternalSessionId(String prefix) {
        return traceSupport.nextInternalSessionId(prefix);
    }

    private record RecoveryExecutionResult(MultiAgentExecutionTrace trace, List<MultiAgentExecutionStep> steps) {
    }
}
