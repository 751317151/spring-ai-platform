package com.huah.ai.platform.rag.service;

import com.huah.ai.platform.rag.model.DocumentMetaEntity;
import com.huah.ai.platform.rag.model.DocumentMetaResponse;
import com.huah.ai.platform.rag.model.KnowledgeBaseEntity;
import com.huah.ai.platform.rag.model.KnowledgeBaseRequest;
import com.huah.ai.platform.rag.model.KnowledgeBaseResponse;
import org.springframework.stereotype.Component;

@Component
public class RagResponseAssembler {

    public KnowledgeBaseResponse toResponse(KnowledgeBaseEntity knowledgeBase) {
        return KnowledgeBaseResponse.builder()
                .id(knowledgeBase.getId())
                .name(knowledgeBase.getName())
                .description(knowledgeBase.getDescription())
                .department(knowledgeBase.getDepartment())
                .chunkSize(knowledgeBase.getChunkSize())
                .chunkOverlap(knowledgeBase.getChunkOverlap())
                .chunkStrategy(knowledgeBase.getChunkStrategy())
                .structuredBatchSize(knowledgeBase.getStructuredBatchSize())
                .createdBy(knowledgeBase.getCreatedBy())
                .visibilityScope(knowledgeBase.getVisibilityScope())
                .createdAt(knowledgeBase.getCreatedAt())
                .status(knowledgeBase.getStatus())
                .documentCount(knowledgeBase.getDocumentCount())
                .totalChunks(knowledgeBase.getTotalChunks())
                .build();
    }

    public DocumentMetaResponse toResponse(DocumentMetaEntity documentMeta) {
        return DocumentMetaResponse.builder()
                .id(documentMeta.getId())
                .filename(documentMeta.getFilename())
                .knowledgeBaseId(documentMeta.getKnowledgeBaseId())
                .fileSize(documentMeta.getFileSize())
                .storagePath(documentMeta.getStoragePath())
                .contentType(documentMeta.getContentType())
                .chunkCount(documentMeta.getChunkCount())
                .uploadedBy(documentMeta.getUploadedBy())
                .status(documentMeta.getStatus())
                .errorMessage(documentMeta.getErrorMessage())
                .createdAt(documentMeta.getCreatedAt())
                .indexedAt(documentMeta.getIndexedAt())
                .build();
    }

    public KnowledgeBaseEntity toEntity(KnowledgeBaseRequest request) {
        return KnowledgeBaseEntity.builder()
                .name(request.getName())
                .description(request.getDescription())
                .department(request.getDepartment())
                .chunkSize(request.getChunkSize() != null ? request.getChunkSize() : 1000)
                .chunkOverlap(request.getChunkOverlap() != null ? request.getChunkOverlap() : 200)
                .chunkStrategy(request.getChunkStrategy())
                .structuredBatchSize(request.getStructuredBatchSize() != null ? request.getStructuredBatchSize() : 20)
                .createdBy(request.getCreatedBy())
                .visibilityScope(request.getVisibilityScope())
                .status(request.getStatus())
                .build();
    }
}
