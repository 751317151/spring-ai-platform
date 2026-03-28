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
@TableName("learning_followup_templates")
public class FollowUpTemplateRecord {

    @TableId(type = IdType.INPUT)
    private String id;
    private String userId;
    private String name;
    private String content;
    private Integer sourceCount;
    private Long updatedAt;
}
