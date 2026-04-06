package com.huah.ai.platform.rag.service;

import com.huah.ai.platform.common.exception.BizException;
import com.huah.ai.platform.rag.config.S3Properties;
import com.huah.ai.platform.rag.model.DocumentChunkPreview;
import com.huah.ai.platform.rag.model.DocumentMetaEntity;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RagDocumentContentService {

    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
    private static final String MESSAGE_DOCUMENT_SOURCE_NOT_AVAILABLE = "Document source file is not available.";

    private final RagAdminFacadeService ragAdminFacadeService;
    private final DocumentMetaService documentMetaService;
    private final FileStorageService fileStorageService;
    private final S3Properties s3Properties;

    public List<DocumentChunkPreview> listDocumentChunks(
            Long id, DocumentMetaService.AccessContext accessContext) {
        documentMetaService.getDocument(id, accessContext);
        return documentMetaService.listDocumentChunks(id);
    }

    public ResponseEntity<InputStreamResource> downloadDocument(
            Long id, DocumentMetaService.AccessContext accessContext) {
        DocumentMetaEntity document = ragAdminFacadeService.loadDocument(id, accessContext);
        requireStoragePath(document);
        InputStream inputStream = fileStorageService.download(document.getStoragePath());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(resolveContentType(document)))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + document.getFilename() + "\"")
                .body(new InputStreamResource(inputStream));
    }

    public Map<String, String> previewDocument(
            Long id, DocumentMetaService.AccessContext accessContext) {
        DocumentMetaEntity document = ragAdminFacadeService.loadDocument(id, accessContext);
        requireStoragePath(document);
        return Map.of(
                "previewUrl",
                fileStorageService.generatePresignedUrl(
                        document.getStoragePath(), s3Properties.getPresignedUrlExpiry()),
                "filename",
                document.getFilename(),
                "contentType",
                resolveContentType(document));
    }

    private void requireStoragePath(DocumentMetaEntity document) {
        if (document.getStoragePath() == null) {
            throw new BizException(MESSAGE_DOCUMENT_SOURCE_NOT_AVAILABLE);
        }
    }

    private String resolveContentType(DocumentMetaEntity document) {
        return document.getContentType() != null ? document.getContentType() : DEFAULT_CONTENT_TYPE;
    }
}
