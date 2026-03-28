package com.huah.ai.platform.agent.learning;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("learning_notes")
public class LearningNoteRecord {

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
    private Long createdAt;
    private Long updatedAt;
}
