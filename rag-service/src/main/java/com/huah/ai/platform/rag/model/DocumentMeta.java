package com.huah.ai.platform.rag.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 文档元数据实体
 */
@Data
@Entity
@Table(name = "document_meta")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentMeta {

    @Id
    private String id;

    private String filename;

    private String knowledgeBaseId;

    /** 文件大小（bytes）*/
    private Long fileSize;

    /** 原始存储路径（MinIO/OSS等） */
    private String storagePath;

    private int chunkCount;

    /** 上传人 */
    private String uploadedBy;

    /** 状态: PROCESSING | INDEXED | FAILED */
    @Builder.Default
    private String status = "PROCESSING";

    private String errorMessage;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime indexedAt;
}
