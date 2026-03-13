package com.huah.ai.platform.gateway.model;

import lombok.Data;
import java.util.List;

@Data
public class ChatRequest {
    /** 用户消息 */
    private String message;
    /** 对话历史 */
    private List<HistoryMessage> history;
    /** 系统提示词 */
    private String systemPrompt;
    /** 最大 Token */
    private Integer maxTokens;
    /** 温度 */
    private Double temperature;

    @Data
    public static class HistoryMessage {
        private String role; // user | assistant
        private String content;
    }
}
