package com.huah.ai.platform.rag.controller;

import com.huah.ai.platform.common.dto.Result;
import com.huah.ai.platform.rag.model.DocumentMeta;
import com.huah.ai.platform.rag.model.KnowledgeBase;
import com.huah.ai.platform.rag.model.RagQueryRequest;
import com.huah.ai.platform.rag.model.RagQueryResponse;
import com.huah.ai.platform.rag.service.DocumentIngestionService;
import com.huah.ai.platform.rag.service.DocumentMetaService;
import com.huah.ai.platform.rag.service.RagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * RAG 知识库接口
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/rag")
@RequiredArgsConstructor
public class RagController {

    private final RagService ragService;
    private final DocumentIngestionService ingestionService;
    private final DocumentMetaService metaService;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    // ============ 知识库问答 ============

    /**
     * 普通问答（返回 answer + sources）
     */
    @PostMapping("/query")
    public Result<RagQueryResponse> query(@RequestBody RagQueryRequest req) {
        long start = System.currentTimeMillis();
        int topK = req.getTopK() != null ? req.getTopK() : 5;

        // 并行：生成答案 + 检索来源文档
        String answer = ragService.query(req.getQuestion(), req.getKnowledgeBaseId(), topK);
        List<Document> sourceDocs = ragService.search(req.getQuestion(), req.getKnowledgeBaseId(), topK);

        List<RagQueryResponse.SourceDocument> sources = sourceDocs.stream()
                .map(doc -> RagQueryResponse.SourceDocument.builder()
                        .filename((String) doc.getMetadata().getOrDefault("filename", "未知文件"))
                        .content(doc.getText() != null ? doc.getText().substring(0, Math.min(200, doc.getText().length())) : "")
                        .score((double) doc.getMetadata().getOrDefault("distance", 0.0))
                        .build())
                .toList();

        return Result.ok(RagQueryResponse.builder()
                .answer(answer)
                .sources(sources)
                .latencyMs(System.currentTimeMillis() - start)
                .build());
    }

    /**
     * 流式问答（SSE）— 前端监听 data: {chunk, done}
     */
    @PostMapping(value = "/query/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter queryStream(@RequestBody RagQueryRequest req) {
        SseEmitter emitter = new SseEmitter(60_000L);
        executor.submit(() -> {
            try {
                int topK = req.getTopK() != null ? req.getTopK() : 5;
                ragService.queryStream(req.getQuestion(), req.getKnowledgeBaseId(), topK)
                    .doOnNext(chunk -> {
                        try { emitter.send(SseEmitter.event().data(Map.of("chunk", chunk, "done", false))); }
                        catch (IOException e) { emitter.completeWithError(e); }
                    })
                    .doOnComplete(() -> {
                        try { emitter.send(SseEmitter.event().data(Map.of("chunk", "", "done", true))); emitter.complete(); }
                        catch (IOException e) { emitter.completeWithError(e); }
                    })
                    .doOnError(emitter::completeWithError)
                    .subscribe();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    /**
     * 纯向量相似性检索
     */
    @PostMapping("/search")
    public Result<List<Document>> search(
            @RequestParam String query,
            @RequestParam String knowledgeBaseId,
            @RequestParam(defaultValue = "5") int topK) {
        return Result.ok(ragService.search(query, knowledgeBaseId, topK));
    }

    // ============ 知识库管理 ============

    @PostMapping("/knowledge-bases")
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
    public Result<KnowledgeBase> updateKnowledgeBase(@PathVariable String id, @RequestBody KnowledgeBase kb) {
        return Result.ok(metaService.updateKnowledgeBase(id, kb));
    }

    @DeleteMapping("/knowledge-bases/{id}")
    public Result<Void> deleteKnowledgeBase(@PathVariable String id) {
        metaService.deleteKnowledgeBase(id);
        return Result.ok(null);
    }

    // ============ 文档管理 ============

    /**
     * 上传单文档并向量化入库
     * X-User-Id 使用 defaultValue，前端未传时不报错
     */
    @PostMapping("/documents/upload")
    public Result<DocumentMeta> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("knowledgeBaseId") String knowledgeBaseId,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId) {
        DocumentMeta meta = ingestionService.ingestDocument(file, knowledgeBaseId, userId);
        return Result.ok(meta);
    }

    /**
     * 批量上传
     */
    @PostMapping("/documents/batch-upload")
    public Result<List<DocumentMeta>> batchUpload(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam("knowledgeBaseId") String knowledgeBaseId,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId) {
        List<DocumentMeta> results = files.stream()
                .map(f -> ingestionService.ingestDocument(f, knowledgeBaseId, userId))
                .toList();
        return Result.ok(results);
    }
}
