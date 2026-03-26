package com.huah.ai.platform.agent.service;

import com.huah.ai.platform.agent.dto.SessionConfigResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionRuntimeInstructionBuilderTest {

    private final SessionRuntimeInstructionBuilder builder = new SessionRuntimeInstructionBuilder();

    @Test
    void shouldBuildRuntimeInstructionFromSessionConfig() {
        SessionConfigResponse config = SessionConfigResponse.builder()
                .model("gpt-4.1")
                .temperature(0.2d)
                .maxContextMessages(6)
                .knowledgeEnabled(Boolean.FALSE)
                .systemPromptTemplate("先给结论，再补细节")
                .build();

        String instruction = builder.build(config);

        assertTrue(instruction.contains("先给结论，再补细节"));
        assertTrue(instruction.contains("6"));
        assertTrue(instruction.contains("gpt-4.1"));
        assertTrue(instruction.contains("[会话配置]"));
    }

    @Test
    void shouldReturnEmptyInstructionWhenConfigIsEmpty() {
        SessionConfigResponse config = SessionConfigResponse.builder().build();
        String instruction = builder.build(config);

        assertTrue(instruction.isEmpty());
    }
}
