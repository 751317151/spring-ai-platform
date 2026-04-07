package com.huah.ai.platform.common.persistence.audit;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_evidence_feedback")
public class AiEvidenceFeedbackEntity {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long responseId;
    private String chunkId;
    private Long knowledgeBaseId;
    private String feedback;
    private String comment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
