package com.huah.ai.platform.rag.service;

import com.huah.ai.platform.rag.model.DocumentMetaEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class FailedDocumentRetryScheduler {

    private final DocumentMetaService documentMetaService;
    private final DocumentIngestionService documentIngestionService;

    @Value("${rag.retry.failed.enabled:false}")
    private boolean enabled;

    @Value("${rag.retry.failed.batch-size:5}")
    private int batchSize;

    @Scheduled(
            initialDelayString = "${rag.retry.failed.initial-delay-ms:60000}",
            fixedDelayString = "${rag.retry.failed.fixed-delay-ms:300000}"
    )
    public void retryFailedDocuments() {
        if (!enabled) {
            return;
        }

        List<DocumentMetaEntity> documents = documentMetaService.listRetryableFailedDocuments(batchSize);
        if (documents.isEmpty()) {
            return;
        }

        for (DocumentMetaEntity document : documents) {
            try {
                documentIngestionService.retryDocument(document.getId());
            } catch (Exception e) {
                log.warn("失败文档自动重试失败: docId={}, error={}", document.getId(), e.getMessage());
            }
        }
    }
}
