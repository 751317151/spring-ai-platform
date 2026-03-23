package com.huah.ai.platform.agent.dto;

import lombok.Data;

@Data
public class SessionToggleRequest {
    private Boolean pinned;
    private Boolean archived;
}
