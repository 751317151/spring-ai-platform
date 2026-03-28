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
@TableName("learning_favorites")
public class LearningFavoriteRecord {

    @TableId(type = IdType.INPUT)
    private String id;
    private String userId;
    private String responseId;
    private String role;
    private String content;
    private String agentType;
    private String sessionId;
    private String sessionSummary;
    private Integer sourceMessageIndex;
    private Long createdAt;
    private Long lastCollectedAt;
    private Integer duplicateCount;
    private String tagsJson;
    private String sessionConfigSnapshotJson;
}
