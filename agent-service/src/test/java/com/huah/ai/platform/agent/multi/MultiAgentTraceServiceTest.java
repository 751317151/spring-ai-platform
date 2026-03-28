package com.huah.ai.platform.agent.multi;

import com.huah.ai.platform.agent.dto.MultiAgentTraceResponse;
import com.huah.ai.platform.agent.memory.ConversationMemoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MultiAgentTraceServiceTest {

    private MultiAgentOrchestrator orchestrator;
    private MultiAgentExecutionTraceMapper traceMapper;
    private MultiAgentExecutionStepMapper stepMapper;
    private ConversationMemoryService memoryService;
    private MultiAgentTraceService multiAgentTraceService;

    @BeforeEach
    void setUp() {
        orchestrator = mock(MultiAgentOrchestrator.class);
        traceMapper = mock(MultiAgentExecutionTraceMapper.class);
        stepMapper = mock(MultiAgentExecutionStepMapper.class);
        memoryService = mock(ConversationMemoryService.class);
        multiAgentTraceService = new MultiAgentTraceService(orchestrator, traceMapper, stepMapper, memoryService);
    }

    @Test
    void shouldRecoverTraceBySkippingTargetStep() {
        MultiAgentExecutionTrace sourceTrace = buildSourceTrace();
        List<MultiAgentExecutionStep> sourceSteps = buildSourceSteps(true);

        when(traceMapper.selectByTraceIdAndUserId("trace-source", "u-1")).thenReturn(sourceTrace);
        when(stepMapper.selectByTraceId("trace-source")).thenReturn(sourceSteps);
        when(orchestrator.critique(anyString(), anyString(), anyString()))
                .thenReturn(new MultiAgentOrchestrator.StepResult("critic-final", 9, 5, 30L));

        MultiAgentTraceResponse response = multiAgentTraceService.recoverTrace("u-1", "trace-source", 2, "skip", new MultiAgentExecutionListener() {});

        assertEquals("RECOVERED_SUCCESS", response.getStatus());
        assertEquals("skip", response.getRecoveryAction());
        assertEquals(3, response.getSteps().size());
        assertTrue(response.getSteps().get(1).getSkipped());
        assertEquals("skip", response.getSteps().get(1).getRecoveryAction());
        assertEquals("trace-source", response.getRecoverySourceTraceId());
        verify(traceMapper).insert(org.mockito.ArgumentMatchers.any(MultiAgentExecutionTrace.class));
        verify(stepMapper, times(3)).insert(org.mockito.ArgumentMatchers.any(MultiAgentExecutionStep.class));
    }

    @Test
    void shouldRecoverTraceByReplayingFromTargetStep() {
        MultiAgentExecutionTrace sourceTrace = buildSourceTrace();
        List<MultiAgentExecutionStep> sourceSteps = buildSourceSteps(true);

        when(traceMapper.selectByTraceIdAndUserId("trace-source", "u-1")).thenReturn(sourceTrace);
        when(stepMapper.selectByTraceId("trace-source")).thenReturn(sourceSteps);
        when(orchestrator.executeWithTools(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new MultiAgentOrchestrator.StepResult("executor-replayed", 11, 7, 40L));
        when(orchestrator.critique(anyString(), anyString(), anyString()))
                .thenReturn(new MultiAgentOrchestrator.StepResult("critic-replayed", 8, 6, 28L));

        MultiAgentTraceResponse response = multiAgentTraceService.recoverTrace("u-1", "trace-source", 2, "replay", new MultiAgentExecutionListener() {});

        assertEquals("RECOVERED_SUCCESS", response.getStatus());
        assertEquals("replay", response.getRecoveryAction());
        assertEquals("reuse", response.getSteps().get(0).getRecoveryAction());
        assertEquals("replay", response.getSteps().get(1).getRecoveryAction());
        assertEquals("executor-replayed", response.getSteps().get(1).getOutputSummary());

        ArgumentCaptor<MultiAgentExecutionTrace> traceCaptor = ArgumentCaptor.forClass(MultiAgentExecutionTrace.class);
        verify(traceMapper).insert(traceCaptor.capture());
        assertEquals("trace-source", traceCaptor.getValue().getParentTraceId());
    }

    @Test
    void shouldRejectRecoverForNonRecoverableStep() {
        MultiAgentExecutionTrace sourceTrace = buildSourceTrace();
        List<MultiAgentExecutionStep> sourceSteps = buildSourceSteps(false);

        when(traceMapper.selectByTraceIdAndUserId("trace-source", "u-1")).thenReturn(sourceTrace);
        when(stepMapper.selectByTraceId("trace-source")).thenReturn(sourceSteps);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> multiAgentTraceService.recoverTrace("u-1", "trace-source", 2, "retry", new MultiAgentExecutionListener() {}));

        assertTrue(ex.getMessage().contains("不支持恢复"));
    }

    @Test
    void shouldReplayArchivedTraceAsFreshExecution() {
        when(orchestrator.planTask(anyString(), anyString()))
                .thenReturn(new MultiAgentOrchestrator.StepResult("plan", 4, 2, 10L));
        when(orchestrator.executeWithTools(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new MultiAgentOrchestrator.StepResult("execute", 6, 3, 20L));
        when(orchestrator.critique(anyString(), anyString(), anyString()))
                .thenReturn(new MultiAgentOrchestrator.StepResult("final", 5, 4, 15L));
        when(traceMapper.selectByTraceIdAndUserId(anyString(), org.mockito.ArgumentMatchers.eq("u-1")))
                .thenAnswer(invocation -> {
                    String replayTraceId = invocation.getArgument(0, String.class);
                    return MultiAgentExecutionTrace.builder()
                            .id("replayed-id")
                            .traceId(replayTraceId)
                            .userId("u-1")
                            .sessionId("archived-session")
                            .agentType("multi")
                            .requestSummary("archived task")
                            .finalSummary("final")
                            .status("SUCCESS")
                            .parentTraceId("trace-archived")
                            .recoverySourceTraceId("trace-archived")
                            .recoveryAction("archive-replay")
                            .stepCount(3)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();
                });
        when(stepMapper.selectByTraceId(anyString())).thenReturn(List.of());

        MultiAgentTraceResponse response = multiAgentTraceService.replayArchivedTrace(
                "u-1",
                "trace-archived",
                "archived task",
                new MultiAgentExecutionListener() {});

        assertEquals("archive-replay", response.getRecoveryAction());
        assertEquals("trace-archived", response.getParentTraceId());
        assertEquals("trace-archived", response.getRecoverySourceTraceId());
        verify(traceMapper).insert(org.mockito.ArgumentMatchers.any(MultiAgentExecutionTrace.class));
    }

    private MultiAgentExecutionTrace buildSourceTrace() {
        return MultiAgentExecutionTrace.builder()
                .id("source-id")
                .traceId("trace-source")
                .userId("u-1")
                .sessionId("session-1")
                .agentType("multi")
                .requestSummary("source request")
                .finalSummary("source final")
                .status("FAILED")
                .totalPromptTokens(30)
                .totalCompletionTokens(18)
                .stepCount(3)
                .createdAt(LocalDateTime.now().minusMinutes(20))
                .updatedAt(LocalDateTime.now().minusMinutes(10))
                .build();
    }

    private List<MultiAgentExecutionStep> buildSourceSteps(boolean recoverable) {
        return List.of(
                buildStep("trace-source", 1, "planner", "Planner", "plan-output", true, true),
                buildStep("trace-source", 2, "executor", "Executor", "executor-output", false, recoverable),
                buildStep("trace-source", 3, "critic", "Critic", "critic-output", true, true)
        );
    }

    private MultiAgentExecutionStep buildStep(String traceId,
                                              int order,
                                              String stage,
                                              String agentName,
                                              String output,
                                              boolean success,
                                              boolean recoverable) {
        return MultiAgentExecutionStep.builder()
                .id(stage + "-id")
                .traceId(traceId)
                .stepOrder(order)
                .stage(stage)
                .agentName(agentName)
                .inputSummary(stage + "-input")
                .outputSummary(output)
                .promptTokens(5)
                .completionTokens(3)
                .latencyMs(20L)
                .success(success)
                .recoverable(recoverable)
                .skipped(false)
                .createdAt(LocalDateTime.now().minusMinutes(order))
                .build();
    }
}
