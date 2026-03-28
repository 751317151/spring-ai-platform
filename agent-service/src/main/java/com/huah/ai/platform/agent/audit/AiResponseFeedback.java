package com.huah.ai.platform.agent.audit;

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
@TableName("ai_response_feedback")
public class AiResponseFeedback {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long responseId;
    private String sourceType;
    private Long knowledgeBaseId;
    private String feedback;
    private String comment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
