package com.huah.ai.platform.agent.controller;

import com.huah.ai.platform.agent.multi.MultiAgentOrchestrator;
import com.huah.ai.platform.agent.service.*;
import com.huah.ai.platform.common.dto.Result;
import com.huah.ai.platform.agent.service.FinanceAssistantAgent;
import com.huah.ai.platform.agent.service.HrAssistantAgent;
import com.huah.ai.platform.agent.service.QcAssistantAgent;
import com.huah.ai.platform.agent.service.RdAssistantAgent;
import com.huah.ai.platform.agent.service.SalesAssistantAgent;
import com.huah.ai.platform.agent.service.SupplyChainAgent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Agent 统一入口控制器
 * 支持: rd | sales | hr | finance | supply-chain | qc | multi
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/agent")
@RequiredArgsConstructor
public class AgentController {

    private final RdAssistantAgent rdAssistant;
    private final SalesAssistantAgent salesAssistant;
    private final HrAssistantAgent hrAssistant;
    private final FinanceAssistantAgent financeAssistant;
    private final SupplyChainAgent supplyChainAgent;
    private final QcAssistantAgent qcAssistant;
    private final MultiAgentOrchestrator multiAgentOrchestrator;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    /** 普通对话（所有 Agent 统一入口） */
    @PostMapping("/{agentType}/chat")
    public Result<String> chat(
            @PathVariable(name = "agentType") String agentType,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId,
            @RequestHeader(value = "X-Session-Id", defaultValue = "default") String sessionId) {

        String message = body.get("message");
        if (message == null || message.isBlank()) return Result.fail(400, "message 不能为空");
        return Result.ok(routeToAgent(agentType, userId, sessionId, message));
    }

    /** 流式对话（SSE）- 所有 Agent 支持 */
    @PostMapping(value = "/{agentType}/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(
            @PathVariable String agentType,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId,
            @RequestHeader(value = "X-Session-Id", defaultValue = "default") String sessionId) {

        SseEmitter emitter = new SseEmitter(120_000L);
        String message = body.get("message");

        executor.submit(() -> {
            try {
                if ("rd".equals(agentType)) {
                    // rd Agent 使用原生 Reactor 流
                    rdAssistant.chatStream(userId, sessionId, message)
                            .doOnNext(chunk -> sendChunk(emitter, chunk))
                            .doOnComplete(() -> sendDone(emitter))
                            .doOnError(emitter::completeWithError)
                            .subscribe();
                } else {
                    // 其他 Agent 同步调用后逐块推送，模拟流式体验
                    String fullResponse = routeToAgent(agentType, userId, sessionId, message);
                    for (int i = 0; i < fullResponse.length(); i += 6) {
                        sendChunk(emitter, fullResponse.substring(i, Math.min(i + 6, fullResponse.length())));
                        Thread.sleep(15);
                    }
                    sendDone(emitter);
                }
            } catch (Exception e) {
                log.error("流式异常: {}", e.getMessage());
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    /** Multi-Agent 复杂任务 */
    @PostMapping("/multi/execute")
    public Result<String> multiAgentExecute(
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId,
            @RequestHeader(value = "X-Session-Id", defaultValue = "default") String sessionId) {
        String task = body.get("task");
        if (task == null || task.isBlank()) return Result.fail(400, "task 不能为空");
        return Result.ok(multiAgentOrchestrator.executeComplexTask(userId, sessionId, task));
    }

    private String routeToAgent(String type, String userId, String sessionId, String msg) {
        return switch (type) {
            case "rd"           -> rdAssistant.chat(userId, sessionId, msg);
            case "sales"        -> salesAssistant.chat(userId, sessionId, msg);
            case "hr"           -> hrAssistant.chat(userId, sessionId, msg);
            case "finance"      -> financeAssistant.chat(userId, sessionId, msg);
            case "supply-chain" -> supplyChainAgent.chat(userId, sessionId, msg);
            case "qc"           -> qcAssistant.chat(userId, sessionId, msg);
            case "multi"        -> multiAgentOrchestrator.executeComplexTask(userId, sessionId, msg);
            default             -> throw new IllegalArgumentException("未知 Agent: " + type);
        };
    }

    private void sendChunk(SseEmitter emitter, String chunk) {
        try { emitter.send(SseEmitter.event().data(Map.of("chunk", chunk, "done", false))); }
        catch (IOException e) { emitter.completeWithError(e); }
    }

    private void sendDone(SseEmitter emitter) {
        try { emitter.send(SseEmitter.event().data(Map.of("chunk", "", "done", true))); emitter.complete(); }
        catch (IOException e) { emitter.completeWithError(e); }
    }
}
