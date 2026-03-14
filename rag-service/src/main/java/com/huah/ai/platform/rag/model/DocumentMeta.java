package com.huah.ai.platform.rag.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 文档元数据实体
 */
@Data
@TableName("document_meta")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentMeta {

    @TableId(type = IdType.ASSIGN_UUID)
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
