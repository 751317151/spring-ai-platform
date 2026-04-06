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
@TableName("learning_favorites")
public class LearningFavoriteEntity {

    @TableId(type = IdType.INPUT)
    private Long id;
    private String userId;
    private Long responseId;
    private String role;
    private String content;
    private String agentType;
    private String sessionId;
    private String sessionSummary;
    private Integer sourceMessageIndex;
    @Builder.Default
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt = LocalDateTime.now();
    @Builder.Default
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt = LocalDateTime.now();
    private LocalDateTime lastCollectedAt;
    private Integer duplicateCount;
    private String tagsJson;
    private String sessionConfigSnapshotJson;
}

