package com.huah.ai.platform.rag.controller;

import com.huah.ai.platform.common.dto.Result;
import com.huah.ai.platform.common.exception.BizException;
import com.huah.ai.platform.rag.config.S3Properties;
import com.huah.ai.platform.rag.model.DocumentMeta;
import com.huah.ai.platform.rag.model.KnowledgeBase;
import com.huah.ai.platform.rag.model.RagQueryRequest;
import com.huah.ai.platform.rag.model.RagQueryResponse;
import com.huah.ai.platform.rag.service.DocumentIngestionService;
import com.huah.ai.platform.rag.service.DocumentMetaService;
import com.huah.ai.platform.rag.service.FileStorageService;
import com.huah.ai.platform.rag.service.RagService;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * RAG 知识库接口。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/rag")
@RequiredArgsConstructor
public class RagController {

    private final RagService ragService;
    private final DocumentIngestionService ingestionService;
    private final DocumentMetaService metaService;
    private final FileStorageService fileStorageService;
    private final S3Properties s3Properties;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @PostMapping("/query")
    public Result<RagQueryResponse> query(@RequestBody RagQueryRequest req) {
        long start = System.currentTimeMillis();
        int topK = req.getTopK() != null ? req.getTopK() : 5;

        String answer = ragService.query(req.getQuestion(), req.getKnowledgeBaseId(), topK);
        List<Document> sourceDocs = ragService.search(req.getQuestion(), req.getKnowledgeBaseId(), topK);

        List<RagQueryResponse.SourceDocument> sources = sourceDocs.stream()
                .map(doc -> {
                    Object scoreObj = doc.getMetadata().getOrDefault("distance", 0.0);
                    double score = (scoreObj instanceof Number) ? ((Number) scoreObj).doubleValue() : 0.0;

                    return RagQueryResponse.SourceDocument.builder()
                            .filename((String) doc.getMetadata().getOrDefault("filename", "未知文件"))
                            .content(doc.getText() != null ? doc.getText().substring(0, Math.min(200, doc.getText().length())) : "")
                            .score(score)
                            .build();
                })
                .toList();

        return Result.ok(RagQueryResponse.builder()
                .answer(answer)
                .sources(sources)
                .latencyMs(System.currentTimeMillis() - start)
                .build());
    }

    @PostMapping(value = "/query/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter queryStream(@RequestBody RagQueryRequest req) {
        SseEmitter emitter = new SseEmitter(60_000L);
        executor.submit(() -> {
            try {
                int topK = req.getTopK() != null ? req.getTopK() : 5;
                ragService.queryStream(req.getQuestion(), req.getKnowledgeBaseId(), topK)
                        .doOnNext(chunk -> {
                            try {
                                emitter.send(SseEmitter.event().data(Map.of("chunk", chunk, "done", false)));
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        })
                        .doOnComplete(() -> {
                            try {
                                emitter.send(SseEmitter.event().data(Map.of("chunk", "", "done", true)));
                                emitter.complete();
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        })
                        .doOnError(emitter::completeWithError)
                        .subscribe();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    @PostMapping("/search")
    public Result<List<Document>> search(
            @RequestParam String query,
            @RequestParam String knowledgeBaseId,
            @RequestParam(defaultValue = "5") int topK) {
        return Result.ok(ragService.search(query, knowledgeBaseId, topK));
    }

    @PostMapping("/knowledge-bases")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<KnowledgeBase> createKnowledgeBase(@RequestBody KnowledgeBase kb) {
        return Result.ok(metaService.createKnowledgeBase(kb));
    }

    @GetMapping("/knowledge-bases")
    public Result<List<KnowledgeBase>> listKnowledgeBases() {
        return Result.ok(metaService.listKnowledgeBases());
    }

    @GetMapping("/knowledge-bases/{id}")
    public Result<KnowledgeBase> getKnowledgeBase(@PathVariable String id) {
        return Result.ok(metaService.getKnowledgeBase(id));
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
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId) {
        DocumentMeta meta = ingestionService.ingestDocument(file, knowledgeBaseId, userId);
        return Result.ok(meta);
    }

    @PostMapping("/documents/batch-upload")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<List<DocumentMeta>> batchUpload(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam("knowledgeBaseId") String knowledgeBaseId,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId) {
        List<DocumentMeta> results = files.stream()
                .map(f -> ingestionService.ingestDocument(f, knowledgeBaseId, userId))
                .toList();
        return Result.ok(results);
    }

    @GetMapping("/documents")
    public Result<List<DocumentMeta>> listDocuments(@RequestParam("knowledgeBaseId") String knowledgeBaseId) {
        return Result.ok(metaService.listDocuments(knowledgeBaseId));
    }

    @GetMapping("/documents/{id}")
    public Result<DocumentMeta> getDocument(@PathVariable("id") String id) {
        return Result.ok(metaService.getDocument(id));
    }

    @PostMapping("/documents/{id}/retry")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<DocumentMeta> retryDocument(@PathVariable("id") String id) {
        return Result.ok(ingestionService.retryDocument(id));
    }

    @GetMapping("/documents/retry-candidates")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<List<DocumentMeta>> retryCandidates(@RequestParam(defaultValue = "20") int limit) {
        return Result.ok(metaService.listRetryableFailedDocuments(limit));
    }

    @GetMapping("/documents/{id}/download")
    public ResponseEntity<InputStreamResource> downloadDocument(@PathVariable("id") String id) {
        DocumentMeta doc = metaService.getDocument(id);
        if (doc.getStoragePath() == null) {
            throw new BizException("该文档没有原始文件存储记录");
        }
        InputStream inputStream = fileStorageService.download(doc.getStoragePath());
        String contentType = doc.getContentType() != null ? doc.getContentType() : "application/octet-stream";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + doc.getFilename() + "\"")
                .body(new InputStreamResource(inputStream));
    }

    @GetMapping("/documents/{id}/preview")
    public Result<Map<String, String>> previewDocument(@PathVariable("id") String id) {
        DocumentMeta doc = metaService.getDocument(id);
        if (doc.getStoragePath() == null) {
            throw new BizException("该文档没有原始文件存储记录");
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
}
