package com.huah.ai.platform.monitor.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ModelStatResponse {
    @JsonProperty("model_id")
    private String modelId;
    private long count;
    @JsonProperty("avg_latency")
    private long avgLatency;
    private long errors;
}

