package com.huah.ai.platform.monitor.service;

import com.huah.ai.platform.monitor.model.AlertsResponse;
import com.huah.ai.platform.monitor.model.FeedbackOverviewResponse;
import com.huah.ai.platform.monitor.model.MonitorOverviewResponse;
import com.huah.ai.platform.monitor.model.MonitorScreenResponse;
import java.util.Collections;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MonitorScreenServiceTest {

    @Test
    void getScreenSnapshotShouldReturnLiveQueryResultOnly() {
        MonitorQueryService monitorQueryService = mock(MonitorQueryService.class);
        when(monitorQueryService.getOverview()).thenReturn(MonitorOverviewResponse.builder().totalRequests(12).build());
        when(monitorQueryService.getHourlyStats()).thenReturn(Collections.emptyList());
        when(monitorQueryService.getAgentStats()).thenReturn(Collections.emptyList());
        when(monitorQueryService.getTopUsers()).thenReturn(Collections.emptyList());
        when(monitorQueryService.getRegionHeat()).thenReturn(Collections.emptyList());
        when(monitorQueryService.getFailureSamples(8)).thenReturn(Collections.emptyList());
        when(monitorQueryService.getFeedbackOverview()).thenReturn(new FeedbackOverviewResponse());
        when(monitorQueryService.getAlerts()).thenReturn(AlertsResponse.builder().activeAlerts(0).alerts(Collections.emptyList()).build());

        MonitorScreenService service = new MonitorScreenService(monitorQueryService);
        MonitorScreenResponse response = service.getScreenSnapshot();

        assertEquals(12, response.getOverview().getTotalRequests());
        assertTrue(response.getRegionHeat().isEmpty());
        assertTrue(response.getTopUsers().isEmpty());
        assertTrue(response.getHourlyStats().isEmpty());
    }
}
