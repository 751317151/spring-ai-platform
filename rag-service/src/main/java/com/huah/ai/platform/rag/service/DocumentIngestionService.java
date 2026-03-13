package com.huah.ai.platform.rag.service;

import com.huah.ai.platform.common.exception.BizException;
import com.huah.ai.platform.rag.model.DocumentMeta;
import com.huah.ai.platform.rag.model.KnowledgeBase;
import com.huah.ai.platform.rag.parser.ExcelDocumentParser;
import com.huah.ai.platform.rag.parser.StructuredDocumentParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 文档 ETL 入库服务
 * 支持 PDF、Word、Excel、TXT、HTML、Markdown
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

    private final VectorStore vectorStore;
    private final DocumentMetaService metaService;
    private final ExcelDocumentParser excelParser;
    private final StructuredDocumentParser structuredParser;

    /**
     * 上传并入库文档
     */
    public DocumentMeta ingestDocument(MultipartFile file, String knowledgeBaseId, String uploadedBy) {
        String filename = file.getOriginalFilename();
        log.info("开始入库文档: {}, 知识库: {}", filename, knowledgeBaseId);

        // 1. 验证知识库存在
        KnowledgeBase kb = metaService.getKnowledgeBase(knowledgeBaseId);

        // 2. 解析文档
        List<Document> documents = parseDocument(file);
        log.info("文档解析完成: {}, 共 {} 段", filename, documents.size());

        // 3. 添加元数据
        String docId = UUID.randomUUID().toString();
        documents.forEach(doc -> doc.getMetadata().putAll(Map.of(
                "doc_id", docId,
                "kb_id", knowledgeBaseId,
                "filename", filename,
                "uploaded_by", uploadedBy,
                "file_type", getFileExtension(filename)
        )));

        // 4. 切片
        TokenTextSplitter splitter = new TokenTextSplitter(
                kb.getChunkSize(),     // chunk大小
                kb.getChunkOverlap(),  // 重叠
                5, 10000, true
        );
        List<Document> chunks = splitter.apply(documents);
        log.info("文档切片完成: {}, 共 {} 个chunk", filename, chunks.size());

        // 5. 向量化存储
        vectorStore.add(chunks);
        log.info("文档向量化完成: {}", filename);

        // 6. 保存元数据
        DocumentMeta meta = DocumentMeta.builder()
                .id(docId)
                .filename(filename)
                .knowledgeBaseId(knowledgeBaseId)
                .chunkCount(chunks.size())
                .uploadedBy(uploadedBy)
                .status("INDEXED")
                .build();
        metaService.saveDocumentMeta(meta);

        return meta;
    }

    /**
     * 根据文件类型选择解析器
     */
    private List<Document> parseDocument(MultipartFile file) {
        String ext = getFileExtension(file.getOriginalFilename()).toLowerCase();
        try {
            byte[] bytes = file.getBytes();
            Resource resource = new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };
            return switch (ext) {
                case "pdf" -> parsePdf(resource);
                case "xlsx", "xls" -> excelParser.parse(resource);
                case "doc", "docx", "ppt", "pptx", "html", "htm", "txt", "md" -> parseTika(resource);
                default -> throw new BizException("不支持的文件格式: " + ext);
            };
        } catch (IOException e) {
            throw new BizException("文档读取失败: " + e.getMessage());
        }
    }

    private List<Document> parsePdf(Resource resource) {
        PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                .withPageTopMargin(0)
                .withPagesPerDocument(1)
                .build();
        PagePdfDocumentReader reader = new PagePdfDocumentReader(resource, config);
        return reader.get();
    }

    private List<Document> parseTika(Resource resource) {
        TikaDocumentReader reader = new TikaDocumentReader(resource);
        return reader.get();
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "unknown";
        return filename.substring(filename.lastIndexOf('.') + 1);
    }
}
