package com.huah.ai.platform.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentChunkPreview {

    private String id;

    private int chunkIndex;

    private String content;

    private String preview;

    private Integer charCount;
}
