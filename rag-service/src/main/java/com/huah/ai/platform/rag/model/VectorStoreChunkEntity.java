package com.huah.ai.platform.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VectorStoreChunkEntity {

    private String chunkId;

    private Long documentId;

    private Integer chunkIndex;

    private String filename;

    private String preview;

    private String content;

    private double lexicalScore;
}
