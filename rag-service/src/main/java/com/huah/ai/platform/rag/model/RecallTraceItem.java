package com.huah.ai.platform.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecallTraceItem {

    private String source;

    private String query;

    private int returnedCount;
}
