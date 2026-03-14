package com.huah.ai.platform.agent.controller;

import com.huah.ai.platform.agent.memory.ConversationMemoryService;
import com.huah.ai.platform.agent.multi.MultiAgentOrchestrator;
import com.huah.ai.platform.agent.service.FinanceAssistantAgent;
import com.huah.ai.platform.agent.service.HrAssistantAgent;
import com.huah.ai.platform.agent.service.QcAssistantAgent;
import com.huah.ai.platform.agent.service.RdAssistantAgent;
import com.huah.ai.platform.agent.service.SalesAssistantAgent;
import com.huah.ai.platform.agent.service.SupplyChainAgent;
import com.huah.ai.platform.common.dto.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

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
    private final ConversationMemoryService memoryService;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Autowired
    private ChatClient chatClient;

    /** 普通对话（所有 Agent 统一入口） */
    @PostMapping("/{agentType}/chat")
    public Result<String> chat(
            @PathVariable(name = "agentType") String agentType,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId,
            @RequestHeader(value = "X-Session-Id", defaultValue = "default") String sessionId) {

        String message = body.get("message");
        if (message == null || message.isBlank()) return Result.fail(400, "message 不能为空");

        log.info("[Chat] 收到请求 agent={}, userId={}, sessionId={}, messageLength={}",
                agentType, userId, sessionId, message.length());
        log.info("[Chat] 用户输入 agent={}, userId={}, message={}",
                agentType, userId, truncate(message, 500));

        long startTime = System.currentTimeMillis();
        try {
            String response = routeToAgent(agentType, userId, sessionId, message);
            long latency = System.currentTimeMillis() - startTime;
            log.info("[Chat] 模型输出 agent={}, userId={}, latency={}ms, responseLength={}, response={}",
                    agentType, userId, latency,
                    response != null ? response.length() : 0,
                    truncate(response, 500));
            return Result.ok(response);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            log.error("[Chat] 调用失败 agent={}, userId={}, latency={}ms, error={}",
                    agentType, userId, latency, e.getMessage(), e);
            throw e;
        }
    }

    /** 流式对话（SSE）- 所有 Agent 支持 */
    @PostMapping(value = "/{agentType}/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(
            @PathVariable(name = "agentType") String agentType,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId,
            @RequestHeader(value = "X-Session-Id", defaultValue = "default") String sessionId) {

        SseEmitter emitter = new SseEmitter(180_000L);
        String message = body.get("message");

        log.info("[Stream] 收到请求 agent={}, userId={}, sessionId={}, messageLength={}",
                agentType, userId, sessionId, message != null ? message.length() : 0);
        log.info("[Stream] 用户输入 agent={}, userId={}, message={}",
                agentType, userId, truncate(message, 500));

        if (message == null || message.isBlank()) {
            log.warn("[Stream] 消息为空, agent={}, userId={}", agentType, userId);
            sendChunk(emitter, "message 不能为空");
            sendDone(emitter);
            return emitter;
        }

        long startTime = System.currentTimeMillis();
        AtomicInteger chunkCount = new AtomicInteger(0);
        StringBuilder fullResponse = new StringBuilder();

        executor.submit(() -> {
            try {
                if ("multi".equals(agentType)) {
                    log.info("[Stream] Multi-Agent 分步流式, userId={}", userId);
                    String internalId = sessionId + "-";

                    // Step 1: Planner
                    sendChunk(emitter, "**[Planner] 正在分析任务...**\n\n");
                    String plan = multiAgentOrchestrator.planTask(message, internalId + "-planner");
                    sendChunk(emitter, plan + "\n\n---\n\n");

                    // Step 2: Executor
                    sendChunk(emitter, "**[Executor] 正在执行任务...**\n\n");
                    String executionResult = multiAgentOrchestrator.executeWithTools(message, plan, internalId + "-executor");
                    sendChunk(emitter, executionResult + "\n\n---\n\n");

                    // Step 3: Critic
                    sendChunk(emitter, "**[Critic] 正在评审结果...**\n\n");
                    String finalResult = multiAgentOrchestrator.critique(message, executionResult, internalId + "-critic");
                    sendChunk(emitter, finalResult);

                    // 保存用户输入和最终结果到主会话，供前端查询历史
                    memoryService.saveExchange(sessionId, message, finalResult);

                    long latency = System.currentTimeMillis() - startTime;
                    log.info("[Stream] Multi-Agent 完成 userId={}, latency={}ms", userId, latency);
                    sendDone(emitter);
                } else {
                    Flux<String> flux = routeToAgentStream(agentType, userId, sessionId, message);
                    flux.doOnNext(chunk -> {
                                chunkCount.incrementAndGet();
                                fullResponse.append(chunk);
                                sendChunk(emitter, chunk);
                            })
                            .doOnComplete(() -> {
                                long latency = System.currentTimeMillis() - startTime;
                                String resp = fullResponse.toString();
                                log.info("[Stream] 模型输出 agent={}, userId={}, latency={}ms, chunks={}, responseLength={}, response={}",
                                        agentType, userId, latency, chunkCount.get(),
                                        resp.length(), truncate(resp, 500));
                                sendDone(emitter);
                            })
                            .doOnError(e -> {
                                long latency = System.currentTimeMillis() - startTime;
                                log.error("[Stream] 流式异常 agent={}, userId={}, latency={}ms, chunks={}, partialResponse={}, error={}",
                                        agentType, userId, latency, chunkCount.get(),
                                        truncate(fullResponse.toString(), 200), e.getMessage(), e);
                                emitter.completeWithError(e);
                            })
                            .subscribe();
                }
            } catch (Exception e) {
                long latency = System.currentTimeMillis() - startTime;
                log.error("[Stream] 执行异常 agent={}, userId={}, latency={}ms, error={}",
                        agentType, userId, latency, e.getMessage(), e);
                emitter.completeWithError(e);
            }
        });

        emitter.onTimeout(() -> {
            long latency = System.currentTimeMillis() - startTime;
            log.warn("[Stream] 连接超时 agent={}, userId={}, latency={}ms, chunks={}",
                    agentType, userId, latency, chunkCount.get());
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

        log.info("[Multi] 收到任务 userId={}, taskLength={}", userId, task.length());
        log.info("[Multi] 用户输入 userId={}, task={}", userId, truncate(task, 500));
        long startTime = System.currentTimeMillis();
        try {
            String result = multiAgentOrchestrator.executeComplexTask(userId, sessionId, task);
            long latency = System.currentTimeMillis() - startTime;
            log.info("[Multi] 模型输出 userId={}, latency={}ms, responseLength={}, response={}",
                    userId, latency,
                    result != null ? result.length() : 0,
                    truncate(result, 500));
            return Result.ok(result);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            log.error("[Multi] 任务失败 userId={}, latency={}ms, error={}",
                    userId, latency, e.getMessage(), e);
            throw e;
        }
    }

    /** 清除会话记忆 */
    @DeleteMapping("/{agentType}/memory")
    public Result<String> clearMemory(
            @PathVariable(name = "agentType") String agentType,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId,
            @RequestHeader(value = "X-Session-Id", defaultValue = "default") String sessionId) {
        log.info("[Memory] 清除记忆 agent={}, userId={}, sessionId={}", agentType, userId, sessionId);
        memoryService.clearMemory(sessionId);
        return Result.ok("会话记忆已清除");
    }

    /** 查询会话历史 */
    @GetMapping("/{agentType}/memory")
    public Result<List<Map<String, String>>> getHistory(
            @PathVariable(name = "agentType") String agentType,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId,
            @RequestHeader(value = "X-Session-Id", defaultValue = "default") String sessionId) {
        log.info("[Memory] 查询历史 agent={}, userId={}, sessionId={}", agentType, userId, sessionId);
        return Result.ok(memoryService.getHistory(sessionId));
    }

    /** 查询当前用户在该助手下的所有会话列表 */
    @GetMapping("/{agentType}/sessions")
    public Result<List<Map<String, String>>> listSessions(
            @PathVariable(name = "agentType") String agentType,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId) {
        String prefix = userId + "-" + agentType + "-";
        log.info("[Sessions] 查询会话列表 agent={}, userId={}, prefix={}", agentType, userId, prefix);
        return Result.ok(memoryService.listSessions(prefix));
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

    private Flux<String> routeToAgentStream(String type, String userId, String sessionId, String msg) {
        return switch (type) {
            case "rd"           -> rdAssistant.chatStream(userId, sessionId, msg);
            case "sales"        -> salesAssistant.chatStream(userId, sessionId, msg);
            case "hr"           -> hrAssistant.chatStream(userId, sessionId, msg);
            case "finance"      -> financeAssistant.chatStream(userId, sessionId, msg);
            case "supply-chain" -> supplyChainAgent.chatStream(userId, sessionId, msg);
            case "qc"           -> qcAssistant.chatStream(userId, sessionId, msg);
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

    private static String truncate(String text, int maxLen) {
        if (text == null) return "null";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...(truncated, total=" + text.length() + ")";
    }

    @RequestMapping(value="/test", produces = "text/html;charset=utf-8")
    public Flux<String> chat(
            @RequestParam("prompt") String prompt,
            @RequestParam("chatId") String chatId,
            @RequestParam("userId") String userId){
        return chatClient.prompt()
                .user(prompt)
                .advisors(a -> a.param(CONVERSATION_ID, chatId))
                .stream()
                .content();
    }
}
