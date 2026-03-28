package com.huah.ai.platform.agent.service;

import com.huah.ai.platform.agent.config.AgentLifecycleProperties;
import com.huah.ai.platform.agent.dto.AgentLogArchiveManifest;
import com.huah.ai.platform.agent.dto.AgentLogCleanupResponse;
import com.huah.ai.platform.agent.dto.AgentLogLifecycleSummaryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentLogLifecycleScheduler {

    private final AgentLogLifecycleService agentLogLifecycleService;
    private final AgentLogArchiveService agentLogArchiveService;
    private final AssistantAgentRegistry assistantAgentRegistry;
    private final AgentLifecycleProperties agentLifecycleProperties;

    @Scheduled(
            fixedDelayString = "#{@agentLifecycleProperties.automation.fixedDelayMs}",
            initialDelayString = "#{@agentLifecycleProperties.automation.initialDelayMs}"
    )
    public void runLifecycleGovernance() {
        if (!agentLifecycleProperties.getAutomation().isEnabled()) {
            return;
        }
        Set<String> agentTypes = new LinkedHashSet<>(assistantAgentRegistry.getRegisteredAgentTypes());
        agentTypes.add("multi");
        boolean dryRun = agentLifecycleProperties.getAutomation().isDryRun();
        for (String agentType : agentTypes) {
            AgentLogLifecycleSummaryResponse summary = agentLogLifecycleService.buildSummary(agentType);
            log.info("[AgentLifecycle] agent={}, dryRun={}, summary={}",
                    agentType,
                    dryRun,
                    summary.getSummary());
            if (summary.getTotalArchiveCandidateCount() > 0 && agentLifecycleProperties.getArchive().isEnabled()) {
                AgentLogArchiveManifest manifest = agentLogArchiveService.createManifest(agentType, summary, dryRun);
                log.info("[AgentLifecycle] agent={}, archiveManifest={}", agentType, manifest.getManifestPath());
            }
            if (summary.getTotalDeleteCandidateCount() <= 0) {
                continue;
            }
            AgentLogCleanupResponse cleanup = agentLogLifecycleService.cleanup(agentType, dryRun);
            log.info("[AgentLifecycle] agent={}, cleanup={}", agentType, cleanup.getSummary());
        }
    }
}
