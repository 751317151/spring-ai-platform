package com.huah.ai.platform.agent.service;

import com.huah.ai.platform.agent.config.AgentLifecycleProperties;
import com.huah.ai.platform.agent.dto.AgentLogArchiveManifest;
import com.huah.ai.platform.agent.dto.AgentLogCleanupResponse;
import com.huah.ai.platform.agent.dto.AgentLogLifecycleSummaryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentLogLifecycleSchedulerTest {

    private AgentLogLifecycleService agentLogLifecycleService;
    private AgentLogArchiveService agentLogArchiveService;
    private AssistantAgentRegistry assistantAgentRegistry;
    private AgentLifecycleProperties properties;
    private AgentLogLifecycleScheduler scheduler;

    @BeforeEach
    void setUp() {
        agentLogLifecycleService = mock(AgentLogLifecycleService.class);
        agentLogArchiveService = mock(AgentLogArchiveService.class);
        assistantAgentRegistry = mock(AssistantAgentRegistry.class);
        properties = new AgentLifecycleProperties();
        scheduler = new AgentLogLifecycleScheduler(agentLogLifecycleService, agentLogArchiveService, assistantAgentRegistry, properties);
    }

    @Test
    void shouldSkipWhenAutomationDisabled() {
        properties.getAutomation().setEnabled(false);

        scheduler.runLifecycleGovernance();

        verify(agentLogLifecycleService, never()).buildSummary("rd");
        verify(agentLogArchiveService, never()).createManifest(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void shouldRunSummaryAndCleanupForRegisteredAgents() {
        properties.getAutomation().setEnabled(true);
        properties.getAutomation().setDryRun(true);
        when(assistantAgentRegistry.getRegisteredAgentTypes()).thenReturn(Set.of("rd"));
        when(agentLogLifecycleService.buildSummary("rd")).thenReturn(summary("rd", 2));
        when(agentLogLifecycleService.buildSummary("multi")).thenReturn(summary("multi", 0));
        when(agentLogArchiveService.createManifest(eq("rd"), org.mockito.ArgumentMatchers.any(), eq(true))).thenReturn(AgentLogArchiveManifest.builder()
                .agentType("rd")
                .manifestPath("data/agent-lifecycle-archive/rd-20260327010101.json")
                .build());
        when(agentLogLifecycleService.cleanup("rd", true)).thenReturn(AgentLogCleanupResponse.builder()
                .agentType("rd")
                .dryRun(true)
                .summary("dryRun")
                .build());

        scheduler.runLifecycleGovernance();

        verify(agentLogLifecycleService, times(1)).buildSummary("rd");
        verify(agentLogLifecycleService, times(1)).buildSummary("multi");
        verify(agentLogArchiveService, times(1)).createManifest(eq("rd"), org.mockito.ArgumentMatchers.any(), eq(true));
        verify(agentLogLifecycleService, times(1)).cleanup("rd", true);
        verify(agentLogLifecycleService, never()).cleanup("multi", true);
    }

    private AgentLogLifecycleSummaryResponse summary(String agentType, long deleteCandidateCount) {
        return AgentLogLifecycleSummaryResponse.builder()
                .agentType(agentType)
                .totalArchiveCandidateCount(deleteCandidateCount)
                .totalDeleteCandidateCount(deleteCandidateCount)
                .summary(agentType)
                .buckets(List.of())
                .build();
    }
}
