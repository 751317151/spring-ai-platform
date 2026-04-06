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

@Data
@TableName("knowledge_bases")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeBaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String name;
    private String description;
    private String department;

    @Builder.Default
    private int chunkSize = 1000;

    @Builder.Default
    private int chunkOverlap = 200;

    @Builder.Default
    private String chunkStrategy = "TOKEN";

    @Builder.Default
    private int structuredBatchSize = 20;

    private String createdBy;

    @Builder.Default
    private String visibilityScope = "DEPARTMENT";

    @Builder.Default
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Builder.Default
    private String status = "ACTIVE";

    @Builder.Default
    private int documentCount = 0;

    @Builder.Default
    private int totalChunks = 0;
}
