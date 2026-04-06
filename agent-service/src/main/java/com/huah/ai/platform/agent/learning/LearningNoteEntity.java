package com.huah.ai.platform.agent.learning;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("learning_notes")
public class LearningNoteEntity {

    @TableId(type = IdType.INPUT)
    private Long id;
    private String userId;
    private String title;
    private String content;
    private String sourceType;
    private Long relatedFavoriteId;
    private String relatedSessionId;
    private String relatedAgentType;
    private String relatedSessionSummary;
    private Integer relatedMessageIndex;
    private String tagsJson;
    @Builder.Default
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt = LocalDateTime.now();
    @Builder.Default
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt = LocalDateTime.now();
}

