package com.huah.ai.platform.rag.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 知识库实体
 */
@Data
@TableName("knowledge_bases")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeBase {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String name;
    private String description;

    /** 所属部门/业务线 */
    private String department;

    /** 文档切片大小（token） */
    @Builder.Default
    private int chunkSize = 1000;

    /** 切片重叠（token） */
    @Builder.Default
    private int chunkOverlap = 200;

    /** 创建人 */
    private String createdBy;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /** 状态: ACTIVE | DISABLED */
    @Builder.Default
    private String status = "ACTIVE";

    /** 总文档数 */
    @Builder.Default
    private int documentCount = 0;

    /** 总 chunk 数 */
    @Builder.Default
    private int totalChunks = 0;
}
