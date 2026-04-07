package com.huah.ai.platform.monitor.service;

import com.huah.ai.platform.monitor.model.MonitorScreenResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MonitorScreenService {

    private final MonitorQueryService monitorQueryService;

    public MonitorScreenResponse getScreenSnapshot() {
        return MonitorScreenResponse.builder()
                .overview(monitorQueryService.getOverview())
                .hourlyStats(monitorQueryService.getHourlyStats())
                .agentStats(monitorQueryService.getAgentStats())
                .topUsers(monitorQueryService.getTopUsers())
                .regionHeat(monitorQueryService.getRegionHeat())
                .failureSamples(monitorQueryService.getFailureSamples(8))
                .feedbackOverview(monitorQueryService.getFeedbackOverview())
                .alerts(monitorQueryService.getAlerts())
                .build();
    }
}
