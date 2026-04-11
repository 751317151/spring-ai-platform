package com.huah.ai.platform.rag.controller;

import com.huah.ai.platform.common.dto.Result;
import com.huah.ai.platform.common.web.RequestOriginResolver;
import com.huah.ai.platform.rag.model.DocumentChunkPreview;
import com.huah.ai.platform.rag.model.KnowledgeBaseRequest;
import com.huah.ai.platform.rag.model.DocumentMetaResponse;
import com.huah.ai.platform.rag.model.EvidenceFeedbackRequest;
import com.huah.ai.platform.rag.model.KnowledgeBaseResponse;
import com.huah.ai.platform.rag.model.RagEvaluationOverview;
import com.huah.ai.platform.rag.model.RagEvaluationSample;
import com.huah.ai.platform.rag.model.RagQueryRequest;
import com.huah.ai.platform.rag.model.ResponseFeedbackRequest;
import com.huah.ai.platform.rag.service.DocumentMetaService;
import com.huah.ai.platform.rag.service.RagAdminFacadeService;
import com.huah.ai.platform.rag.service.RagAuditService;
import com.huah.ai.platform.rag.service.RagDocumentContentService;
import com.huah.ai.platform.rag.service.RagQueryFacadeService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.core.io.InputStreamResource;
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

@RestController
@RequestMapping("/api/v1/rag")
@RequiredArgsConstructor
public class RagController {

    private final RagAuditService ragAuditService;
    private final RagAdminFacadeService ragAdminFacadeService;
    private final RagQueryFacadeService ragQueryFacadeService;
    private final RagDocumentContentService ragDocumentContentService;
    private final RequestOriginResolver requestOriginResolver;

    @PostMapping(value = "/query/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter queryStream(@RequestBody RagQueryRequest request, HttpServletRequest servletRequest) {
        return ragQueryFacadeService.queryStream(
                request,
                resolveUserId(servletRequest),
                resolveAccessContext(servletRequest),
                requestOriginResolver.resolve(servletRequest));
    }

    @PostMapping("/feedback")
    public Result<String> submitFeedback(
            @RequestBody ResponseFeedbackRequest request, HttpServletRequest servletRequest) {
        ragAuditService.submitFeedback(
                resolveUserId(servletRequest),
                request.getResponseId(),
                null,
                request.getFeedback(),
                request.getComment());
        return Result.ok("反馈提交成功");
    }

    @PostMapping("/feedback/evidence")
    public Result<String> submitEvidenceFeedback(
            @RequestBody EvidenceFeedbackRequest request, HttpServletRequest servletRequest) {
        ragAuditService.submitEvidenceFeedback(
                resolveUserId(servletRequest),
                request.getResponseId(),
                request.getChunkId(),
                request.getKnowledgeBaseId(),
                request.getFeedback(),
                request.getComment());
        return Result.ok("证据反馈提交成功");
    }

    @PostMapping("/search")
    public Result<List<Document>> search(
            @RequestParam String query,
            @RequestParam Long knowledgeBaseId,
            @RequestParam(defaultValue = "5") int topK,
            HttpServletRequest servletRequest) {
        return Result.ok(
                ragQueryFacadeService.search(query, knowledgeBaseId, topK, resolveAccessContext(servletRequest)));
    }

    @GetMapping("/evaluation/overview")
    public Result<RagEvaluationOverview> evaluationOverview(
            @RequestParam(name = "knowledgeBaseId", required = false) String knowledgeBaseId,
            HttpServletRequest servletRequest) {
        return Result.ok(ragQueryFacadeService.getEvaluationOverview(
                parseLongValue(knowledgeBaseId), resolveAccessContext(servletRequest)));
    }

    @GetMapping("/evaluation/low-rated")
    public Result<List<RagEvaluationSample>> lowRatedSamples(
            @RequestParam(name = "knowledgeBaseId", required = false) String knowledgeBaseId,
            @RequestParam(name = "limit", defaultValue = "20") int limit,
            HttpServletRequest servletRequest) {
        return Result.ok(ragQueryFacadeService.getLowRatedSamples(
                parseLongValue(knowledgeBaseId), limit, resolveAccessContext(servletRequest)));
    }

    @PostMapping("/knowledge-bases")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<KnowledgeBaseResponse> createKnowledgeBase(@RequestBody KnowledgeBaseRequest request) {
        return Result.ok(ragAdminFacadeService.createKnowledgeBase(request));
    }

    @GetMapping("/knowledge-bases")
    public Result<List<KnowledgeBaseResponse>> listKnowledgeBases(HttpServletRequest request) {
        return Result.ok(ragAdminFacadeService.listKnowledgeBases(resolveAccessContext(request)));
    }

    @GetMapping("/knowledge-bases/{id}")
    public Result<KnowledgeBaseResponse> getKnowledgeBase(@PathVariable Long id, HttpServletRequest request) {
        return Result.ok(ragAdminFacadeService.getKnowledgeBase(id, resolveAccessContext(request)));
    }

    @PutMapping("/knowledge-bases/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<KnowledgeBaseResponse> updateKnowledgeBase(
            @PathVariable Long id, @RequestBody KnowledgeBaseRequest request) {
        return Result.ok(ragAdminFacadeService.updateKnowledgeBase(id, request));
    }

    @DeleteMapping("/knowledge-bases/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<Void> deleteKnowledgeBase(@PathVariable Long id) {
        ragAdminFacadeService.deleteKnowledgeBase(id);
        return Result.ok(null);
    }

    @PostMapping("/documents/upload")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<DocumentMetaResponse> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("knowledgeBaseId") Long knowledgeBaseId,
            @RequestParam(name = "replaceExisting", defaultValue = "false") boolean replaceExisting,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId) {
        return Result.ok(ragAdminFacadeService.uploadDocument(file, knowledgeBaseId, userId, replaceExisting));
    }

    @PostMapping("/documents/batch-upload")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<List<DocumentMetaResponse>> batchUpload(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam("knowledgeBaseId") Long knowledgeBaseId,
            @RequestParam(name = "replaceExisting", defaultValue = "false") boolean replaceExisting,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId) {
        return Result.ok(ragAdminFacadeService.batchUpload(files, knowledgeBaseId, userId, replaceExisting));
    }

    @GetMapping("/documents")
    public Result<List<DocumentMetaResponse>> listDocuments(
            @RequestParam("knowledgeBaseId") Long knowledgeBaseId, HttpServletRequest request) {
        return Result.ok(ragAdminFacadeService.listDocuments(knowledgeBaseId, resolveAccessContext(request)));
    }

    @GetMapping("/documents/{id}")
    public Result<DocumentMetaResponse> getDocument(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(ragAdminFacadeService.getDocument(id, resolveAccessContext(request)));
    }

    @GetMapping("/documents/{id}/chunks")
    public Result<List<DocumentChunkPreview>> listDocumentChunks(
            @PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(ragDocumentContentService.listDocumentChunks(id, resolveAccessContext(request)));
    }

    @PostMapping("/documents/{id}/retry")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<DocumentMetaResponse> retryDocument(@PathVariable("id") Long id) {
        return Result.ok(ragAdminFacadeService.retryDocument(id));
    }

    @PostMapping("/documents/{id}/reindex")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<DocumentMetaResponse> reindexDocument(@PathVariable("id") Long id) {
        return Result.ok(ragAdminFacadeService.reindexDocument(id));
    }

    @GetMapping("/documents/retry-candidates")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<List<DocumentMetaResponse>> retryCandidates(@RequestParam(defaultValue = "20") int limit) {
        return Result.ok(ragAdminFacadeService.listRetryCandidates(limit));
    }

    @GetMapping("/documents/{id}/download")
    public ResponseEntity<InputStreamResource> downloadDocument(
            @PathVariable("id") Long id, HttpServletRequest request) {
        return ragDocumentContentService.downloadDocument(id, resolveAccessContext(request));
    }

    @GetMapping("/documents/{id}/preview")
    public Result<Map<String, String>> previewDocument(
            @PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(ragDocumentContentService.previewDocument(id, resolveAccessContext(request)));
    }

    @DeleteMapping("/documents/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<Void> deleteDocument(@PathVariable("id") Long id) {
        ragAdminFacadeService.deleteDocument(id);
        return Result.ok(null);
    }

    private String resolveUserId(HttpServletRequest request) {
        Object value = request.getAttribute("X-User-Id");
        if (value != null) {
            return String.valueOf(value);
        }
        String header = request.getHeader("X-User-Id");
        return header != null && !header.isBlank() ? header : "anonymous";
    }

    private Long parseLongValue(String value) {
        if (value == null || value.isBlank() || "anonymous".equalsIgnoreCase(value)) {
            return null;
        }
        return Long.parseLong(value);
    }

    private DocumentMetaService.AccessContext resolveAccessContext(HttpServletRequest request) {
        String userId = resolveUserId(request);
        Object departmentAttr = request.getAttribute("X-Department");
        Object rolesAttr = request.getAttribute("X-Roles");
        String department =
                departmentAttr != null ? String.valueOf(departmentAttr) : request.getHeader("X-Department");
        String roles = rolesAttr != null ? String.valueOf(rolesAttr) : request.getHeader("X-Roles");
        boolean isAdmin = roles != null && roles.contains("ROLE_ADMIN");
        return new DocumentMetaService.AccessContext(userId, department, isAdmin);
    }
}
