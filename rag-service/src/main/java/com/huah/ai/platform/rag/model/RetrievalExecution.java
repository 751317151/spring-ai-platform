package com.huah.ai.platform.rag.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetrievalExecution {

    private List<RetrievedChunk> selectedChunks;

    private RetrievalDebugInfo retrievalDebug;
}
