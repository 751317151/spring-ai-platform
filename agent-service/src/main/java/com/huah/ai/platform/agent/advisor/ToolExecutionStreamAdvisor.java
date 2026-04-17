package com.huah.ai.platform.agent.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.Map;

/**
 * Intercepts tool call events during streaming and pushes status updates
 * to the SSE emitter so the frontend can show "Searching...", "Querying database..." etc.
 */
@Slf4j
public class ToolExecutionStreamAdvisor implements StreamAdvisor {

    private static final int DEFAULT_ORDER = 50;
    private static final Map<String, String> TOOL_LABELS = Map.ofEntries(
            Map.entry("searchWeb", "Searching the web"),
            Map.entry("summarizeUrl", "Reading web page"),
            Map.entry("queryJira", "Querying Jira"),
            Map.entry("queryConfluence", "Querying Confluence"),
            Map.entry("querySonar", "Querying SonarQube"),
            Map.entry("getCurrentWeather", "Checking weather"),
            Map.entry("getWeatherForecast", "Getting weather forecast"),
            Map.entry("executeSqlQuery", "Querying database"),
            Map.entry("callConnector", "Calling external API")
    );

    private final SseEmitter emitter;

    public ToolExecutionStreamAdvisor(SseEmitter emitter) {
        this.emitter = emitter;
    }

    @Override
    public String getName() {
        return "ToolExecutionStreamAdvisor";
    }

    @Override
    public int getOrder() {
        return DEFAULT_ORDER;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        return chain.nextStream(request)
                .doOnNext(response -> {
                    if (response.chatResponse() == null || response.chatResponse().getResults() == null) {
                        return;
                    }
                    response.chatResponse().getResults().forEach(generation -> {
                        if (generation.getOutput() != null && generation.getOutput().getToolCalls() != null) {
                            generation.getOutput().getToolCalls().forEach(toolCall -> {
                                String toolName = toolCall.name();
                                String label = TOOL_LABELS.getOrDefault(toolName, "Executing: " + toolName);
                                sendToolStatus(toolName, label);
                            });
                        }
                    });
                });
    }

    private void sendToolStatus(String toolName, String label) {
        try {
            emitter.send(SseEmitter.event()
                    .name("tool_status")
                    .data(Map.of(
                            "type", "tool_call",
                            "toolName", toolName,
                            "status", "executing",
                            "label", label
                    )));
        } catch (IOException e) {
            log.debug("[ToolStatus] failed to send status for tool={}: {}", toolName, e.getMessage());
        }
    }
}
