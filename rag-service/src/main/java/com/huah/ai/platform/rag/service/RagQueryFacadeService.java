package com.huah.ai.platform.rag.service;

import com.huah.ai.platform.common.web.RequestOrigin;
import com.huah.ai.platform.rag.model.RagEvaluationOverview;
import com.huah.ai.platform.rag.model.RagEvaluationSample;
import com.huah.ai.platform.rag.model.RagQueryRequest;
import com.huah.ai.platform.rag.model.RagQueryResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
    private final DocumentMetaService documentMetaService;
    private final RagAuditService ragAuditService;
    @Qualifier("ragControllerExecutor")
    private final ExecutorService executor;

    public RagQueryResponse query(
            RagQueryRequest request,
            String userId,
            DocumentMetaService.AccessContext accessContext,
            RequestOrigin requestOrigin) {
        long start = System.currentTimeMillis();
        int topK = resolveTopK(request);
        try {
            documentMetaService.ensureKnowledgeBaseAccessible(request.getKnowledgeBaseId(), accessContext);
            String answer = ragService.query(request.getQuestion(), request.getKnowledgeBaseId(), topK);
            List<RagQueryResponse.SourceDocument> sources =
                    mapSourceDocuments(ragService.search(request.getQuestion(), request.getKnowledgeBaseId(), topK));
            long latency = System.currentTimeMillis() - start;
            Long responseId = ragAuditService.saveQueryLog(
                    userId,
                    request.getKnowledgeBaseId(),
                    request.getQuestion(),
                    answer,
                    latency,
                    true,
                    null,
                    requestOrigin);
            return RagQueryResponse.builder()
                    .responseId(responseId)
                    .answer(answer)
                    .sources(sources)
                    .latencyMs(latency)
                    .build();
        } catch (Exception ex) {
            ragAuditService.saveQueryLog(
                    userId,
                    request.getKnowledgeBaseId(),
                    request.getQuestion(),
                    null,
                    System.currentTimeMillis() - start,
                    false,
                    ex.getMessage(),
                    requestOrigin);
            throw ex;
        }
    }

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
            List<RagQueryResponse.SourceDocument> sources =
                    mapSourceDocuments(ragService.search(request.getQuestion(), request.getKnowledgeBaseId(), topK));
            List<String> chunks = new ArrayList<>();
            ragService.queryStream(request.getQuestion(), request.getKnowledgeBaseId(), topK)
                    .doOnNext(chunk -> handleStreamChunk(emitter, chunks, chunk))
                    .doOnComplete(() -> completeStream(emitter, request, userId, start, chunks, sources, requestOrigin))
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
            List<RagQueryResponse.SourceDocument> sources,
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
                    "responseId", String.valueOf(responseId))));
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

    private List<RagQueryResponse.SourceDocument> mapSourceDocuments(List<Document> sourceDocs) {
        return sourceDocs.stream()
                .map(doc -> {
                    Object scoreObj = doc.getMetadata().getOrDefault("distance", 0.0);
                    double score = scoreObj instanceof Number number ? number.doubleValue() : 0.0;
                    return RagQueryResponse.SourceDocument.builder()
                            .documentId(asLong(doc.getMetadata().get("doc_id")))
                            .chunkId(asString(doc.getId() != null ? doc.getId() : UUID.randomUUID()))
                            .chunkIndex(asInteger(doc.getMetadata().get("chunk_index")))
                            .filename(asString(doc.getMetadata().getOrDefault("filename", "unknown")))
                            .preview(asString(doc.getMetadata().get("chunk_preview")))
                            .content(doc.getText() != null ? doc.getText() : "")
                            .score(score)
                            .build();
                })
                .toList();
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : Long.parseLong(text);
    }

    private Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Integer.parseInt(text);
        }
        return null;
    }
}
