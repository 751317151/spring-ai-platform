package com.huah.ai.platform.monitor.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackOverviewResponse {
    private long totalCount;
    private long positiveCount;
    private long negativeCount;
    private double positiveRate;
}

