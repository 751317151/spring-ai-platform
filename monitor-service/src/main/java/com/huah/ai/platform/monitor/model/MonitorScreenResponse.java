package com.huah.ai.platform.monitor.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class MonitorScreenResponse {
    MonitorOverviewResponse overview;
    List<HourlyStatResponse> hourlyStats;
    List<AgentStatResponse> agentStats;
    List<TopUserResponse> topUsers;
    List<RegionHeatResponse> regionHeat;
    List<FailureSampleResponse> failureSamples;
    FeedbackOverviewResponse feedbackOverview;
    AlertsResponse alerts;
}
