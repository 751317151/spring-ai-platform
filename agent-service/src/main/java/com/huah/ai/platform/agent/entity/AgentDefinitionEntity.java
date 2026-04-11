package com.huah.ai.platform.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
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
@TableName("ai_agent_definitions")
public class AgentDefinitionEntity {

    @TableId
    private Long id;
    private String agentCode;
    private String agentName;
    private String description;
    private String icon;
    private String color;
    private String systemPrompt;
    private String defaultModel;
    private String toolCodes;
    private String mcpServerCodes;
    private Boolean enabled;
    private Integer sortOrder;
    private Integer dailyTokenLimit;
    private String assistantProfile;
    private Boolean systemDefined;
    private String createdBy;
    private String updatedBy;
    @Builder.Default
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt = LocalDateTime.now();
    @Builder.Default
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
