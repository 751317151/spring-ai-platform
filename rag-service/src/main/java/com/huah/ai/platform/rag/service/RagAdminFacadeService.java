package com.huah.ai.platform.rag.service;

import com.huah.ai.platform.rag.model.DocumentMetaEntity;
import com.huah.ai.platform.rag.model.DocumentMetaResponse;
import com.huah.ai.platform.rag.model.KnowledgeBaseEntity;
import com.huah.ai.platform.rag.model.KnowledgeBaseRequest;
import com.huah.ai.platform.rag.model.KnowledgeBaseResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RagAdminFacadeService {

    private final DocumentMetaService metaService;
    private final DocumentIngestionService ingestionService;
    private final RagResponseAssembler ragResponseAssembler;

    public KnowledgeBaseResponse createKnowledgeBase(KnowledgeBaseRequest request) {
        return ragResponseAssembler.toResponse(metaService.createKnowledgeBase(ragResponseAssembler.toEntity(request)));
    }

    public List<KnowledgeBaseResponse> listKnowledgeBases(DocumentMetaService.AccessContext context) {
        return metaService.listKnowledgeBases(context).stream().map(ragResponseAssembler::toResponse).toList();
    }

    public KnowledgeBaseResponse getKnowledgeBase(Long id, DocumentMetaService.AccessContext context) {
        return ragResponseAssembler.toResponse(metaService.getKnowledgeBase(id, context));
    }

    public KnowledgeBaseResponse updateKnowledgeBase(Long id, KnowledgeBaseRequest request) {
        return ragResponseAssembler.toResponse(metaService.updateKnowledgeBase(id, ragResponseAssembler.toEntity(request)));
    }

    public void deleteKnowledgeBase(Long id) {
        metaService.deleteKnowledgeBase(id);
    }

    public DocumentMetaResponse uploadDocument(MultipartFile file, Long knowledgeBaseId, String userId, boolean replaceExisting) {
        return ragResponseAssembler.toResponse(
                ingestionService.ingestDocument(file, knowledgeBaseId, userId, replaceExisting));
    }

    public List<DocumentMetaResponse> batchUpload(List<MultipartFile> files, Long knowledgeBaseId, String userId, boolean replaceExisting) {
        return files.stream()
                .map(file -> ingestionService.ingestDocument(file, knowledgeBaseId, userId, replaceExisting))
                .map(ragResponseAssembler::toResponse)
                .toList();
    }

    public List<DocumentMetaResponse> listDocuments(Long knowledgeBaseId, DocumentMetaService.AccessContext context) {
        return metaService.listDocuments(knowledgeBaseId, context).stream()
                .map(ragResponseAssembler::toResponse)
                .toList();
    }

    public DocumentMetaResponse getDocument(Long id, DocumentMetaService.AccessContext context) {
        return ragResponseAssembler.toResponse(metaService.getDocument(id, context));
    }

    public DocumentMetaEntity loadDocument(Long id, DocumentMetaService.AccessContext context) {
        return metaService.getDocument(id, context);
    }

    public DocumentMetaResponse retryDocument(Long id) {
        return ragResponseAssembler.toResponse(ingestionService.retryDocument(id));
    }

    public DocumentMetaResponse reindexDocument(Long id) {
        return ragResponseAssembler.toResponse(ingestionService.reindexDocument(id));
    }

    public List<DocumentMetaResponse> listRetryCandidates(int limit) {
        return metaService.listRetryableFailedDocuments(limit).stream()
                .map(ragResponseAssembler::toResponse)
                .toList();
    }

    public void deleteDocument(Long id) {
        metaService.deleteDocument(id);
    }
}
