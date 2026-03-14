package com.huah.ai.platform.agent.service;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.ai.chat.model.ChatResponse;

/**
 * Agent 聊天结果，携带响应文本和精确 token 用量
 */
@Getter
@AllArgsConstructor
public class AgentChatResult {
    private final String content;
    private final int promptTokens;
    private final int completionTokens;

    /**
     * 从 Spring AI ChatResponse 中提取精确 token 用量
     */
    public static AgentChatResult fromChatResponse(ChatResponse chatResponse) {
        String text = "";
        int prompt = 0;
        int completion = 0;

        if (chatResponse != null) {
            if (chatResponse.getResult() != null && chatResponse.getResult().getOutput() != null) {
                text = chatResponse.getResult().getOutput().getText();
            }
            if (chatResponse.getMetadata() != null && chatResponse.getMetadata().getUsage() != null) {
                var usage = chatResponse.getMetadata().getUsage();
                prompt = (int) usage.getPromptTokens();
                completion = (int) usage.getCompletionTokens();
            }
        }

        return new AgentChatResult(text, prompt, completion);
    }
}
