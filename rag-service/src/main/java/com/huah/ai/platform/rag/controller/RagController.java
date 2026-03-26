package com.huah.ai.platform.rag.controller;

import com.huah.ai.platform.common.dto.Result;
import com.huah.ai.platform.common.exception.BizException;
import com.huah.ai.platform.rag.config.S3Properties;
import com.huah.ai.platform.rag.model.DocumentChunkPreview;
import com.huah.ai.platform.rag.model.EvidenceFeedbackRequest;
import com.huah.ai.platform.rag.model.DocumentMeta;
import com.huah.ai.platform.rag.model.KnowledgeBase;
import com.huah.ai.platform.rag.model.RagQueryRequest;
import com.huah.ai.platform.rag.model.RagQueryResponse;
import com.huah.ai.platform.rag.model.RagEvaluationOverview;
import com.huah.ai.platform.rag.model.RagEvaluationSample;
import com.huah.ai.platform.rag.model.ResponseFeedbackRequest;
import com.huah.ai.platform.rag.service.DocumentIngestionService;
import com.huah.ai.platform.rag.service.RagAuditService;
import com.huah.ai.platform.rag.service.DocumentMetaService;
import com.huah.ai.platform.rag.service.FileStorageService;
import com.huah.ai.platform.rag.service.RagService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@RestController
@RequestMapping("/api/v1/rag")
@RequiredArgsConstructor
public class RagController {

    private final RagService ragService;
    private final DocumentIngestionService ingestionService;
    private final DocumentMetaService metaService;
    private final FileStorageService fileStorageService;
    private final RagAuditService ragAuditService;
    private final S3Properties s3Properties;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @PostMapping("/query")
    public Result<RagQueryResponse> query(@RequestBody RagQueryRequest req, HttpServletRequest request) {
        long start = System.currentTimeMillis();
        int topK = req.getTopK() != null ? req.getTopK() : 5;
        String userId = resolveUserId(request);
        var accessContext = resolveAccessContext(request);

        try {
            metaService.ensureKnowledgeBaseAccessible(req.getKnowledgeBaseId(), accessContext);
            String answer = ragService.query(req.getQuestion(), req.getKnowledgeBaseId(), topK);
            List<RagQueryResponse.SourceDocument> sources = mapSourceDocuments(
                    ragService.search(req.getQuestion(), req.getKnowledgeBaseId(), topK)
            );
            long latency = System.currentTimeMillis() - start;
            String responseId = ragAuditService.saveQueryLog(
                    userId,
                    req.getKnowledgeBaseId(),
                    req.getQuestion(),
                    answer,
                    latency,
                    true,
                    null
            );

            return Result.ok(RagQueryResponse.builder()
                    .responseId(responseId)
                    .answer(answer)
                    .sources(sources)
                    .latencyMs(latency)
                    .build());
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            ragAuditService.saveQueryLog(userId, req.getKnowledgeBaseId(), req.getQuestion(), null, latency, false, e.getMessage());
            throw e;
        }
    }

    @PostMapping(value = "/query/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter queryStream(@RequestBody RagQueryRequest req, HttpServletRequest request) {
        SseEmitter emitter = new SseEmitter(60_000L);
        String userId = resolveUserId(request);
        var accessContext = resolveAccessContext(request);
        executor.submit(() -> {
            try {
                int topK = req.getTopK() != null ? req.getTopK() : 5;
                long start = System.currentTimeMillis();
                metaService.ensureKnowledgeBaseAccessible(req.getKnowledgeBaseId(), accessContext);
                List<RagQueryResponse.SourceDocument> sources = mapSourceDocuments(
                        ragService.search(req.getQuestion(), req.getKnowledgeBaseId(), topK)
                );
                List<String> chunks = new ArrayList<>();

                ragService.queryStream(req.getQuestion(), req.getKnowledgeBaseId(), topK)
                        .doOnNext(chunk -> {
                            chunks.add(chunk);
                            try {
                                emitter.send(SseEmitter.event().data(Map.of("chunk", chunk, "done", false)));
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        })
                        .doOnComplete(() -> {
                            try {
                                String answer = String.join("", chunks);
                                String responseId = ragAuditService.saveQueryLog(
                                        userId,
                                        req.getKnowledgeBaseId(),
                                        req.getQuestion(),
                                        answer,
                                        System.currentTimeMillis() - start,
                                        true,
                                        null
                                );
                                emitter.send(SseEmitter.event().data(Map.of(
                                        "chunk", "",
                                        "done", true,
                                        "sources", sources,
                                        "responseId", responseId
                                )));
                                emitter.complete();
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        })
                        .doOnError(error -> {
                            ragAuditService.saveQueryLog(
                                    userId,
                                    req.getKnowledgeBaseId(),
                                    req.getQuestion(),
                                    null,
                                    System.currentTimeMillis() - start,
                                    false,
                                    error.getMessage()
                            );
                            emitter.completeWithError(error);
                        })
                        .subscribe();
            } catch (Exception e) {
                ragAuditService.saveQueryLog(
                        userId,
                        req.getKnowledgeBaseId(),
                        req.getQuestion(),
                        null,
                        0,
                        false,
                        e.getMessage()
                );
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    @PostMapping("/feedback")
    public Result<String> submitFeedback(@RequestBody ResponseFeedbackRequest req, HttpServletRequest request) {
        ragAuditService.submitFeedback(resolveUserId(request), req.getResponseId(), null, req.getFeedback(), req.getComment());
        return Result.ok("反馈已提交");
    }

    @PostMapping("/feedback/evidence")
    public Result<String> submitEvidenceFeedback(@RequestBody EvidenceFeedbackRequest req, HttpServletRequest request) {
        ragAuditService.submitEvidenceFeedback(
                resolveUserId(request),
                req.getResponseId(),
                req.getChunkId(),
                req.getKnowledgeBaseId(),
                req.getFeedback(),
                req.getComment()
        );
        return Result.ok("证据反馈已提交");
    }

    @PostMapping("/search")
    public Result<List<Document>> search(
            @RequestParam String query,
            @RequestParam String knowledgeBaseId,
            @RequestParam(defaultValue = "5") int topK,
            HttpServletRequest request) {
        metaService.ensureKnowledgeBaseAccessible(knowledgeBaseId, resolveAccessContext(request));
        return Result.ok(ragService.search(query, knowledgeBaseId, topK));
    }

    @GetMapping("/evaluation/overview")
    public Result<RagEvaluationOverview> evaluationOverview(
            @RequestParam(name = "knowledgeBaseId", required = false) String knowledgeBaseId,
            HttpServletRequest request) {
        if (knowledgeBaseId != null && !knowledgeBaseId.isBlank()) {
            metaService.ensureKnowledgeBaseAccessible(knowledgeBaseId, resolveAccessContext(request));
        }
        return Result.ok(ragAuditService.getEvaluationOverview(knowledgeBaseId));
    }

    @GetMapping("/evaluation/low-rated")
    public Result<List<RagEvaluationSample>> lowRatedSamples(
            @RequestParam(name = "knowledgeBaseId", required = false) String knowledgeBaseId,
            @RequestParam(name = "limit", defaultValue = "20") int limit,
            HttpServletRequest request) {
        if (knowledgeBaseId != null && !knowledgeBaseId.isBlank()) {
            metaService.ensureKnowledgeBaseAccessible(knowledgeBaseId, resolveAccessContext(request));
        }
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return Result.ok(ragAuditService.getLowRatedSamples(knowledgeBaseId, safeLimit));
    }

    @PostMapping("/knowledge-bases")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<KnowledgeBase> createKnowledgeBase(@RequestBody KnowledgeBase kb) {
        return Result.ok(metaService.createKnowledgeBase(kb));
    }

    @GetMapping("/knowledge-bases")
    public Result<List<KnowledgeBase>> listKnowledgeBases(HttpServletRequest request) {
        return Result.ok(metaService.listKnowledgeBases(resolveAccessContext(request)));
    }

    @GetMapping("/knowledge-bases/{id}")
    public Result<KnowledgeBase> getKnowledgeBase(@PathVariable String id, HttpServletRequest request) {
        return Result.ok(metaService.getKnowledgeBase(id, resolveAccessContext(request)));
    }

    @PutMapping("/knowledge-bases/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<KnowledgeBase> updateKnowledgeBase(@PathVariable String id, @RequestBody KnowledgeBase kb) {
        return Result.ok(metaService.updateKnowledgeBase(id, kb));
    }

    @DeleteMapping("/knowledge-bases/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<Void> deleteKnowledgeBase(@PathVariable String id) {
        metaService.deleteKnowledgeBase(id);
        return Result.ok(null);
    }

    @PostMapping("/documents/upload")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<DocumentMeta> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("knowledgeBaseId") String knowledgeBaseId,
            @RequestParam(name = "replaceExisting", defaultValue = "false") boolean replaceExisting,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId) {
        return Result.ok(ingestionService.ingestDocument(file, knowledgeBaseId, userId, replaceExisting));
    }

    @PostMapping("/documents/batch-upload")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<List<DocumentMeta>> batchUpload(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam("knowledgeBaseId") String knowledgeBaseId,
            @RequestParam(name = "replaceExisting", defaultValue = "false") boolean replaceExisting,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId) {
        return Result.ok(files.stream()
                .map(f -> ingestionService.ingestDocument(f, knowledgeBaseId, userId, replaceExisting))
                .toList());
    }

    @GetMapping("/documents")
    public Result<List<DocumentMeta>> listDocuments(@RequestParam("knowledgeBaseId") String knowledgeBaseId, HttpServletRequest request) {
        return Result.ok(metaService.listDocuments(knowledgeBaseId, resolveAccessContext(request)));
    }

    @GetMapping("/documents/{id}")
    public Result<DocumentMeta> getDocument(@PathVariable("id") String id, HttpServletRequest request) {
        return Result.ok(metaService.getDocument(id, resolveAccessContext(request)));
    }

    @GetMapping("/documents/{id}/chunks")
    public Result<List<DocumentChunkPreview>> listDocumentChunks(@PathVariable("id") String id, HttpServletRequest request) {
        metaService.getDocument(id, resolveAccessContext(request));
        return Result.ok(metaService.listDocumentChunks(id));
    }

    @PostMapping("/documents/{id}/retry")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<DocumentMeta> retryDocument(@PathVariable("id") String id) {
        return Result.ok(ingestionService.retryDocument(id));
    }

    @PostMapping("/documents/{id}/reindex")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<DocumentMeta> reindexDocument(@PathVariable("id") String id) {
        return Result.ok(ingestionService.reindexDocument(id));
    }

    @GetMapping("/documents/retry-candidates")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<List<DocumentMeta>> retryCandidates(@RequestParam(defaultValue = "20") int limit) {
        return Result.ok(metaService.listRetryableFailedDocuments(limit));
    }

    @GetMapping("/documents/{id}/download")
    public ResponseEntity<InputStreamResource> downloadDocument(@PathVariable("id") String id, HttpServletRequest request) {
        DocumentMeta doc = metaService.getDocument(id, resolveAccessContext(request));
        if (doc.getStoragePath() == null) {
            throw new BizException("Document source file is not available.");
        }
        InputStream inputStream = fileStorageService.download(doc.getStoragePath());
        String contentType = doc.getContentType() != null ? doc.getContentType() : "application/octet-stream";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + doc.getFilename() + "\"")
                .body(new InputStreamResource(inputStream));
    }

    @GetMapping("/documents/{id}/preview")
    public Result<Map<String, String>> previewDocument(@PathVariable("id") String id, HttpServletRequest request) {
        DocumentMeta doc = metaService.getDocument(id, resolveAccessContext(request));
        if (doc.getStoragePath() == null) {
            throw new BizException("Document source file is not available.");
        }
        String url = fileStorageService.generatePresignedUrl(doc.getStoragePath(), s3Properties.getPresignedUrlExpiry());
        return Result.ok(Map.of(
                "previewUrl", url,
                "filename", doc.getFilename(),
                "contentType", doc.getContentType() != null ? doc.getContentType() : "application/octet-stream"
        ));
    }

    @DeleteMapping("/documents/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<Void> deleteDocument(@PathVariable("id") String id) {
        metaService.deleteDocument(id);
        return Result.ok(null);
    }

    private List<RagQueryResponse.SourceDocument> mapSourceDocuments(List<Document> sourceDocs) {
        return sourceDocs.stream()
                .map(doc -> {
                    Object scoreObj = doc.getMetadata().getOrDefault("distance", 0.0);
                    double score = scoreObj instanceof Number number ? number.doubleValue() : 0.0;

                    return RagQueryResponse.SourceDocument.builder()
                            .documentId(asString(doc.getMetadata().get("doc_id")))
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

    private Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Integer.parseInt(text);
        }
        return null;
    }

    private String resolveUserId(HttpServletRequest request) {
        Object value = request.getAttribute("X-User-Id");
        if (value != null) {
            return String.valueOf(value);
        }
        String header = request.getHeader("X-User-Id");
        return header != null && !header.isBlank() ? header : "anonymous";
    }

    private DocumentMetaService.AccessContext resolveAccessContext(HttpServletRequest request) {
        String userId = resolveUserId(request);
        Object departmentAttr = request.getAttribute("X-Department");
        Object rolesAttr = request.getAttribute("X-Roles");
        String department = departmentAttr != null ? String.valueOf(departmentAttr) : request.getHeader("X-Department");
        String roles = rolesAttr != null ? String.valueOf(rolesAttr) : request.getHeader("X-Roles");
        boolean isAdmin = roles != null && roles.contains("ROLE_ADMIN");
        return new DocumentMetaService.AccessContext(userId, department, isAdmin);
    }
}
