package com.huah.ai.platform.agent.service;

import com.huah.ai.platform.agent.dto.SessionConfigResponse;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import reactor.core.publisher.Flux;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseAssistantAgentSessionConfigTest {

    private final StubAssistantAgent agent = new StubAssistantAgent();

    @Test
    void shouldBuildChatOptionsFromSessionConfig() throws Exception {
        SessionConfigResponse config = SessionConfigResponse.builder()
                .model("gpt-4.1")
                .temperature(0.25d)
                .build();

        Method method = BaseAssistantAgent.class.getDeclaredMethod("buildChatOptions", SessionConfigResponse.class);
        method.setAccessible(true);
        ChatOptions options = (ChatOptions) method.invoke(agent, config);

        assertEquals("gpt-4.1", options.getModel());
        assertEquals(0.25d, options.getTemperature());
    }

    @Test
    void shouldPrefixRuntimeInstructionIntoUserMessage() throws Exception {
        Method method = BaseAssistantAgent.class.getDeclaredMethod("enrichMessage", String.class, String.class);
        method.setAccessible(true);

        String enriched = (String) method.invoke(agent, "请继续分析", "[会话配置]\n- 最近参考 6 条消息");

        assertTrue(enriched.startsWith("[会话配置]"));
        assertTrue(enriched.contains("最近参考 6 条消息"));
        assertTrue(enriched.contains("[用户问题]"));
        assertTrue(enriched.endsWith("请继续分析"));
    }

    private static final class StubAssistantAgent extends BaseAssistantAgent {

        private StubAssistantAgent() {
            super("stub", null, null, new SessionRuntimeInstructionBuilder());
        }

        @Override
        public AgentChatResult chat(String userId, String sessionId, String message) {
            return new AgentChatResult(message, 0, 0);
        }

        @Override
        public Flux<ChatResponse> chatStream(String userId, String sessionId, String message) {
            return Flux.empty();
        }
    }
}
