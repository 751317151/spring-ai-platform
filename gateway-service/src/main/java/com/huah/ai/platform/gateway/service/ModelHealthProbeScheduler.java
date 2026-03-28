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
        if (shouldRunProbe(true)) {
            safeRunProbe("startup", false);
        }
    }

    @Scheduled(fixedDelayString = "${ai.models.health-probe.interval-ms:300000}", initialDelayString = "${ai.models.health-probe.interval-ms:300000}")
    public void scheduledProbe() {
        if (shouldRunProbe(false)) {
            safeRunProbe("scheduled", true);
        }
    }

    private boolean shouldRunProbe(boolean startupPhase) {
        ModelRegistryConfig.HealthProbe probe = modelRegistryConfig.getHealthProbe();
        if (probe == null || !probe.isEnabled()) {
            return false;
        }
        return !startupPhase || probe.isRunOnStartup();
    }

    private void safeRunProbe(String trigger, boolean debugLog) {
        if (debugLog) {
            log.debug("Running {} model health probe", trigger);
        } else {
            log.info("Running {} model health probe", trigger);
        }
        try {
            modelGatewayService.probeAllModels();
        } catch (Exception e) {
            log.warn("Model health probe failed during {} execution: {}", trigger, e.getMessage(), e);
        }
    }
}
