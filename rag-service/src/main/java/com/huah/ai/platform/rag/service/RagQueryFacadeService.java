package com.huah.ai.platform.rag.service;

import com.huah.ai.platform.common.web.RequestOrigin;
import com.huah.ai.platform.rag.model.RagEvaluationOverview;
import com.huah.ai.platform.rag.model.RagEvaluationSample;
import com.huah.ai.platform.rag.model.RagQueryRequest;
import com.huah.ai.platform.rag.model.RagSourceDocument;
import com.huah.ai.platform.rag.model.RetrievalExecution;
import com.huah.ai.platform.rag.model.RetrievedChunk;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
@RequiredArgsConstructor
public class RagQueryFacadeService {

    private static final long STREAM_TIMEOUT_MS = 60_000L;

    private final RagService ragService;
    private final RetrievalOrchestrator retrievalOrchestrator;
    private final DocumentMetaService documentMetaService;
    private final RagAuditService ragAuditService;
    @Qualifier("ragControllerExecutor")
    private final ExecutorService executor;

    public SseEmitter queryStream(
            RagQueryRequest request,
            String userId,
            DocumentMetaService.AccessContext accessContext,
            RequestOrigin requestOrigin) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        executor.submit(() -> executeStream(request, userId, accessContext, requestOrigin, emitter));
        return emitter;
    }

    public List<Document> search(
            String query, Long knowledgeBaseId, int topK, DocumentMetaService.AccessContext accessContext) {
        documentMetaService.ensureKnowledgeBaseAccessible(knowledgeBaseId, accessContext);
        return ragService.search(query, knowledgeBaseId, topK);
    }

    public RagEvaluationOverview getEvaluationOverview(
            Long knowledgeBaseId, DocumentMetaService.AccessContext accessContext) {
        if (knowledgeBaseId != null) {
            documentMetaService.ensureKnowledgeBaseAccessible(knowledgeBaseId, accessContext);
        }
        return ragAuditService.getEvaluationOverview(knowledgeBaseId);
    }

    public List<RagEvaluationSample> getLowRatedSamples(
            Long knowledgeBaseId, int limit, DocumentMetaService.AccessContext accessContext) {
        if (knowledgeBaseId != null) {
            documentMetaService.ensureKnowledgeBaseAccessible(knowledgeBaseId, accessContext);
        }
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return ragAuditService.getLowRatedSamples(knowledgeBaseId, safeLimit);
    }

    private void executeStream(
            RagQueryRequest request,
            String userId,
            DocumentMetaService.AccessContext accessContext,
            RequestOrigin requestOrigin,
            SseEmitter emitter) {
        int topK = resolveTopK(request);
        long start = System.currentTimeMillis();
        try {
            documentMetaService.ensureKnowledgeBaseAccessible(request.getKnowledgeBaseId(), accessContext);
            RetrievalExecution retrievalExecution =
                    retrievalOrchestrator.retrieve(request.getQuestion(), request.getKnowledgeBaseId(), topK);
            List<RagSourceDocument> sources = mapSourceDocuments(retrievalExecution.getSelectedChunks());
            List<String> chunks = new ArrayList<>();
            ragService.answerStream(request.getQuestion(), request.getHistory(), retrievalExecution.getSelectedChunks())
                    .doOnNext(chunk -> handleStreamChunk(emitter, chunks, chunk))
                    .doOnComplete(() -> completeStream(
                            emitter,
                            request,
                            userId,
                            start,
                            chunks,
                            sources,
                            retrievalExecution,
                            requestOrigin))
                    .doOnError(error -> failStream(emitter, request, userId, start, error, requestOrigin))
                    .subscribe();
        } catch (Exception ex) {
            ragAuditService.saveQueryLog(
                    userId,
                    request.getKnowledgeBaseId(),
                    request.getQuestion(),
                    null,
                    0,
                    false,
                    ex.getMessage(),
                    requestOrigin);
            emitter.completeWithError(ex);
        }
    }

    private void handleStreamChunk(SseEmitter emitter, List<String> chunks, String chunk) {
        chunks.add(chunk);
        try {
            emitter.send(SseEmitter.event().data(Map.of("chunk", chunk, "done", false)));
        } catch (IOException ex) {
            emitter.completeWithError(ex);
        }
    }

    private void completeStream(
            SseEmitter emitter,
            RagQueryRequest request,
            String userId,
            long start,
            List<String> chunks,
            List<RagSourceDocument> sources,
            RetrievalExecution retrievalExecution,
            RequestOrigin requestOrigin) {
        try {
            String answer = String.join("", chunks);
            Long responseId = ragAuditService.saveQueryLog(
                    userId,
                    request.getKnowledgeBaseId(),
                    request.getQuestion(),
                    answer,
                    System.currentTimeMillis() - start,
                    true,
                    null,
                    requestOrigin);
            emitter.send(SseEmitter.event().data(Map.of(
                    "chunk", "",
                    "done", true,
                    "sources", sources,
                    "responseId", String.valueOf(responseId),
                    "retrievalDebug", retrievalExecution.getRetrievalDebug())));
            emitter.complete();
        } catch (IOException ex) {
            emitter.completeWithError(ex);
        }
    }

    private void failStream(
            SseEmitter emitter,
            RagQueryRequest request,
            String userId,
            long start,
            Throwable error,
            RequestOrigin requestOrigin) {
        ragAuditService.saveQueryLog(
                userId,
                request.getKnowledgeBaseId(),
                request.getQuestion(),
                null,
                System.currentTimeMillis() - start,
                false,
                error.getMessage(),
                requestOrigin);
        emitter.completeWithError(error);
    }

    private int resolveTopK(RagQueryRequest request) {
        return request.getTopK() != null ? request.getTopK() : 5;
    }

    private List<RagSourceDocument> mapSourceDocuments(List<RetrievedChunk> sourceChunks) {
        return sourceChunks.stream()
                .map(chunk -> RagSourceDocument.builder()
                        .documentId(chunk.getDocumentId())
                        .chunkId(chunk.getChunkId())
                        .chunkIndex(chunk.getChunkIndex())
                        .filename(chunk.getFilename())
                        .preview(chunk.getPreview())
                        .content(chunk.getContent())
                        .score(chunk.getRerankScore() > 0
                                ? chunk.getRerankScore()
                                : Math.max(chunk.getSemanticScore(), chunk.getKeywordScore()))
                        .semanticScore(chunk.getSemanticScore())
                        .keywordScore(chunk.getKeywordScore())
                        .recallSources(chunk.getRecallSources())
                        .matchedTerms(chunk.getMatchedTerms())
                        .build())
                .toList();
    }
}
