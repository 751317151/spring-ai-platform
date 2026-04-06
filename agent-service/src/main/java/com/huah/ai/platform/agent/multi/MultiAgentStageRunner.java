package com.huah.ai.platform.agent.multi;

import java.time.LocalDateTime;

final class MultiAgentStageRunner {

    private final MultiAgentOrchestrator multiAgentOrchestrator;
    private final MultiAgentTraceSupport traceSupport;

    MultiAgentStageRunner(MultiAgentOrchestrator multiAgentOrchestrator, MultiAgentTraceSupport traceSupport) {
        this.multiAgentOrchestrator = multiAgentOrchestrator;
        this.traceSupport = traceSupport;
    }

    MultiAgentOrchestrator.StepResult runPlanner(String traceId,
                                                 String task,
                                                 String internalId,
                                                 MultiAgentExecutionListener listener) {
        return runStage(traceId, 1, "planner", "Planner", traceSupport.summarize(task), listener,
                () -> multiAgentOrchestrator.planTask(task, internalId + "-planner"));
    }

    MultiAgentOrchestrator.StepResult runExecutor(String traceId,
                                                  String userId,
                                                  String task,
                                                  String planContent,
                                                  String internalId,
                                                  String executionInput,
                                                  MultiAgentExecutionListener listener) {
        return runStage(traceId, 2, "executor", "Executor", executionInput, listener,
                () -> multiAgentOrchestrator.executeWithTools(
                        userId,
                        task,
                        traceSupport.defaultString(planContent),
                        internalId + "-executor"
                ));
    }

    MultiAgentOrchestrator.StepResult runCritic(String traceId,
                                                String task,
                                                String executionContent,
                                                String internalId,
                                                String criticInput,
                                                MultiAgentExecutionListener listener) {
        return runStage(traceId, 3, "critic", "Critic", criticInput, listener,
                () -> multiAgentOrchestrator.critique(
                        task,
                        traceSupport.defaultString(executionContent),
                        internalId + "-critic"
                ));
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
            listener.onStageCompleted(traceSupport.toStep(
                    traceId,
                    order,
                    stage,
                    label,
                    inputSummary,
                    result,
                    true,
                    null,
                    result.getLatencyMs()
            ));
            return result;
        } catch (RuntimeException ex) {
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
                    .errorMessage(traceSupport.summarize(ex.getMessage()))
                    .recoverable(true)
                    .skipped(false)
                    .createdAt(LocalDateTime.now())
                    .build();
            listener.onStageCompleted(failedStep);
            throw new StageExecutionException(failedStep, ex);
        }
    }

    @FunctionalInterface
    interface StageSupplier {
        MultiAgentOrchestrator.StepResult get();
    }

    static class StageExecutionException extends RuntimeException {
        private final MultiAgentExecutionStep failedStep;

        StageExecutionException(MultiAgentExecutionStep failedStep, Throwable cause) {
            super(cause);
            this.failedStep = failedStep;
        }

        public MultiAgentExecutionStep getFailedStep() {
            return failedStep;
        }
    }
}
