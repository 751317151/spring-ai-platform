package com.huah.ai.platform.auth.model;

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
@TableName("ai_agent_definitions")
public class AiAgentDefinitionEntity {

    @TableId
    private Long id;
    private String agentCode;
    private String agentName;
    private Integer dailyTokenLimit;
    private Boolean enabled;
}
