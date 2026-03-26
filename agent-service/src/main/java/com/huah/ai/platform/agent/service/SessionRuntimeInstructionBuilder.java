package com.huah.ai.platform.agent.service;

import com.huah.ai.platform.agent.dto.SessionConfigResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SessionRuntimeInstructionBuilder {

    public String build(SessionConfigResponse config) {
        if (config == null) {
            return "";
        }

        List<String> lines = new ArrayList<>();
        if (config.getSystemPromptTemplate() != null && !config.getSystemPromptTemplate().isBlank()) {
            lines.add("附加系统要求：" + config.getSystemPromptTemplate().trim());
        }
        if (config.getKnowledgeEnabled() != null) {
            lines.add(Boolean.TRUE.equals(config.getKnowledgeEnabled())
                    ? "知识增强：允许在需要时结合知识库、工具或外部证据回答。"
                    : "知识增强：本次会话优先只基于当前对话内容回答，不主动依赖外部证据。");
        }
        if (config.getMaxContextMessages() != null && config.getMaxContextMessages() > 0) {
            lines.add("上下文窗口：优先参考最近 " + config.getMaxContextMessages() + " 条消息。");
        }
        if (config.getTemperature() != null) {
            if (config.getTemperature() <= 0.3d) {
                lines.add("回答风格：更稳定、保守、精确。");
            } else if (config.getTemperature() >= 0.8d) {
                lines.add("回答风格：允许更发散，并提供更多备选方案。");
            } else {
                lines.add("回答风格：兼顾准确性与创造性。");
            }
        }
        if (config.getModel() != null && !config.getModel().isBlank() && !"auto".equalsIgnoreCase(config.getModel())) {
            lines.add("模型偏好：" + config.getModel().trim() + "。");
        }

        if (lines.isEmpty()) {
            return "";
        }
        return "[会话配置]\n- " + String.join("\n- ", lines);
    }
}
