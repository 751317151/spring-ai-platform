package com.huah.ai.platform.gateway.service;

import com.huah.ai.platform.gateway.config.ModelRegistryConfig;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ModelHealthProbeScheduler {

    private final ModelGatewayService modelGatewayService;
    private final ModelRegistryConfig modelRegistryConfig;

    @PostConstruct
    public void runStartupProbe() {
        ModelRegistryConfig.HealthProbe probe = modelRegistryConfig.getHealthProbe();
        if (probe != null && probe.isEnabled() && probe.isRunOnStartup()) {
            log.info("Running startup model health probe");
            modelGatewayService.probeAllModels();
        }
    }

    @Scheduled(fixedDelayString = "${ai.models.health-probe.interval-ms:300000}", initialDelayString = "${ai.models.health-probe.interval-ms:300000}")
    public void scheduledProbe() {
        ModelRegistryConfig.HealthProbe probe = modelRegistryConfig.getHealthProbe();
        if (probe == null || !probe.isEnabled()) {
            return;
        }
        log.debug("Running scheduled model health probe");
        modelGatewayService.probeAllModels();
    }
}
