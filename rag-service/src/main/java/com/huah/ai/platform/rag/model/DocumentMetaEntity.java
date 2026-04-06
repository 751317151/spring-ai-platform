package com.huah.ai.platform.rag.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文档元数据实体。
 */
@Data
@TableName("document_meta")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentMetaEntity {

    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_INDEXED = "INDEXED";
    public static final String STATUS_FAILED = "FAILED";

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String filename;

    private Long knowledgeBaseId;

    private Long fileSize;

    private String storagePath;

    private String contentType;

    private int chunkCount;

    private String uploadedBy;

    @Builder.Default
    private String status = STATUS_PROCESSING;

    private String errorMessage;

    @Builder.Default
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt = LocalDateTime.now();

    private LocalDateTime indexedAt;
}
