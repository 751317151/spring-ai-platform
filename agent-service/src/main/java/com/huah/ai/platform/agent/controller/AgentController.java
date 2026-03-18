package com.huah.ai.platform.agent.controller;

import com.huah.ai.platform.agent.audit.AiAuditLog;
import com.huah.ai.platform.agent.audit.AiAuditLogMapper;
import com.huah.ai.platform.agent.memory.ConversationMemoryService;
import com.huah.ai.platform.agent.metrics.AiMetricsCollector;
import com.huah.ai.platform.agent.multi.MultiAgentOrchestrator;
import com.huah.ai.platform.agent.security.AgentAccessChecker;
import com.huah.ai.platform.agent.service.AgentChatResult;
import com.huah.ai.platform.agent.service.CodeAssistantAgent;
import com.huah.ai.platform.agent.service.DataAnalysisAssistantAgent;
import com.huah.ai.platform.agent.service.FinanceAssistantAgent;
import com.huah.ai.platform.agent.service.HrAssistantAgent;
import com.huah.ai.platform.agent.service.McpAssistantAgent;
import com.huah.ai.platform.agent.service.QcAssistantAgent;
import com.huah.ai.platform.agent.service.RdAssistantAgent;
import com.huah.ai.platform.agent.service.SalesAssistantAgent;
import com.huah.ai.platform.agent.service.SearchAssistantAgent;
import com.huah.ai.platform.agent.service.SupplyChainAgent;
import com.huah.ai.platform.agent.service.WeatherAssistantAgent;
import com.huah.ai.platform.common.dto.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Agent 统一入口控制器
 * 支持: rd | sales | hr | finance | supply-chain | qc | weather | search | data-analysis | code | mcp | multi
 *
 * 安全机制:
 * 1. JWT 认证（JwtAuthFilter 拦截未携带/无效 Token 的请求）
 * 2. Bot 权限检查（基于 ai_bot_permissions 表的角色/部门匹配）
 * 3. Token 配额控制（基于 Redis 的每日限额）
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
    private final WeatherAssistantAgent weatherAssistant;
    private final SearchAssistantAgent searchAssistant;
    private final DataAnalysisAssistantAgent dataAnalysisAssistant;
    private final CodeAssistantAgent codeAssistant;
    private final MultiAgentOrchestrator multiAgentOrchestrator;
    private final ConversationMemoryService memoryService;
    private final AiAuditLogMapper auditLogMapper;
    private final AgentAccessChecker accessChecker;
    private final AiMetricsCollector metricsCollector;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    // MCP 助手（可选，仅在 MCP 启用时注入）
    @Autowired(required = false)
    private McpAssistantAgent mcpAssistant;

    // Token 预扣量（在 AI 调用前预扣，调用后按实际用量修正）
    private static final int PRE_DEDUCT_TOKENS = 500;

    /** 普通对话（所有 Agent 统一入口） */
    @PostMapping("/{agentType}/chat")
    public Result<String> chat(
            @PathVariable(name = "agentType") String agentType,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-Session-Id", defaultValue = "default") String sessionId,
            HttpServletRequest request) {

        String userId = getUserId(request);
        String roles = getRoles(request);
        String department = getDepartment(request);

        // 权限检查
        String deny = accessChecker.checkPermission(agentType, roles, department);
        if (deny != null) {
            log.warn("[Chat] 权限拒绝 agent={}, userId={}, roles={}, reason={}", agentType, userId, roles, deny);
            return Result.fail(403, deny);
        }

        // Token 配额预检
        String quotaDeny = accessChecker.checkAndConsumeTokens(userId, agentType, PRE_DEDUCT_TOKENS);
        if (quotaDeny != null) {
            log.warn("[Chat] Token 超限 agent={}, userId={}", agentType, userId);
            return Result.fail(429, quotaDeny);
        }

        String message = body.get("message");
        if (message == null || message.isBlank()) return Result.fail(400, "message 不能为空");

        log.info("[Chat] 收到请求 agent={}, userId={}, sessionId={}, messageLength={}",
                agentType, userId, sessionId, message.length());
        log.info("[Chat] 用户输入 agent={}, userId={}, message={}",
                agentType, userId, truncate(message, 500));

        long startTime = System.currentTimeMillis();
        try {
            AgentChatResult result = routeToAgent(agentType, userId, sessionId, message);
            long latency = System.currentTimeMillis() - startTime;
            log.info("[Chat] 模型输出 agent={}, userId={}, latency={}ms, responseLength={}, promptTokens={}, completionTokens={}, response={}",
                    agentType, userId, latency,
                    result.getContent() != null ? result.getContent().length() : 0,
                    result.getPromptTokens(), result.getCompletionTokens(),
                    truncate(result.getContent(), 500));
            // 修正 Token 预扣
            int actualTokens = result.getPromptTokens() + result.getCompletionTokens();
            accessChecker.recordActualTokens(userId, actualTokens, PRE_DEDUCT_TOKENS);
            return Result.ok(result.getContent());
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            log.error("[Chat] 调用失败 agent={}, userId={}, latency={}ms, error={}",
                    agentType, userId, latency, e.getMessage(), e);
            // 调用失败回滚预扣
            accessChecker.recordActualTokens(userId, 0, PRE_DEDUCT_TOKENS);
            memoryService.rollbackLastUserMessage(sessionId);
            return Result.fail(500, "AI 服务暂时不可用，请稍后重试");
        }
    }

    /** 流式对话（SSE）- 所有 Agent 支持 */
    @PostMapping(value = "/{agentType}/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(
            @PathVariable(name = "agentType") String agentType,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-Session-Id", defaultValue = "default") String sessionId,
            HttpServletRequest request) {

        SseEmitter emitter = new SseEmitter(180_000L);

        String userId = getUserId(request);
        String roles = getRoles(request);
        String department = getDepartment(request);

        // 权限检查
        String deny = accessChecker.checkPermission(agentType, roles, department);
        if (deny != null) {
            log.warn("[Stream] 权限拒绝 agent={}, userId={}, roles={}, reason={}", agentType, userId, roles, deny);
            sendChunk(emitter, "[权限不足] " + deny);
            sendDone(emitter);
            return emitter;
        }

        // Token 配额预检
        String quotaDeny = accessChecker.checkAndConsumeTokens(userId, agentType, PRE_DEDUCT_TOKENS);
        if (quotaDeny != null) {
            log.warn("[Stream] Token 超限 agent={}, userId={}", agentType, userId);
            sendChunk(emitter, "[配额不足] " + quotaDeny);
            sendDone(emitter);
            return emitter;
        }

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
        AtomicInteger totalPromptTokens = new AtomicInteger(0);
        AtomicInteger totalCompletionTokens = new AtomicInteger(0);

        metricsCollector.incrementActive();

        executor.submit(() -> {
            try {
                if ("multi".equals(agentType)) {
                    log.info("[Stream] Multi-Agent 分步流式, userId={}", userId);
                    String internalId = sessionId + "-";
                    int totalPrompt = 0;
                    int totalCompletion = 0;

                    // Step 1: Planner
                    sendChunk(emitter, "**[Planner] 正在分析任务...**\n\n");
                    var planResult = multiAgentOrchestrator.planTask(message, internalId + "-planner");
                    sendChunk(emitter, planResult.getContent() + "\n\n---\n\n");
                    totalPrompt += planResult.getPromptTokens();
                    totalCompletion += planResult.getCompletionTokens();

                    // Step 2: Executor
                    sendChunk(emitter, "**[Executor] 正在执行任务...**\n\n");
                    var execResult = multiAgentOrchestrator.executeWithTools(message, planResult.getContent(), internalId + "-executor");
                    sendChunk(emitter, execResult.getContent() + "\n\n---\n\n");
                    totalPrompt += execResult.getPromptTokens();
                    totalCompletion += execResult.getCompletionTokens();

                    // Step 3: Critic
                    sendChunk(emitter, "**[Critic] 正在评审结果...**\n\n");
                    var criticResult = multiAgentOrchestrator.critique(message, execResult.getContent(), internalId + "-critic");
                    sendChunk(emitter, criticResult.getContent());
                    totalPrompt += criticResult.getPromptTokens();
                    totalCompletion += criticResult.getCompletionTokens();

                    memoryService.saveExchange(sessionId, message, criticResult.getContent());

                    metricsCollector.decrementActive();
                    long latency = System.currentTimeMillis() - startTime;
                    log.info("[Stream] Multi-Agent 完成 userId={}, latency={}ms, tokens={}/{}", userId, latency, totalPrompt, totalCompletion);
                    metricsCollector.recordRequest(null, "multi", latency, true, totalPrompt, totalCompletion);
                    saveAuditLog(userId, sessionId, "multi", message, truncate(criticResult.getContent(), 500), latency, true, null, totalPrompt, totalCompletion);
                    int actualTokens = totalPrompt + totalCompletion;
                    accessChecker.recordActualTokens(userId, actualTokens, PRE_DEDUCT_TOKENS);
                    sendDone(emitter);
                } else {
                    Flux<ChatResponse> flux = routeToAgentStream(agentType, userId, sessionId, message);
                    flux.doOnNext(chatResponse -> {
                                chunkCount.incrementAndGet();
                                String chunk = "";
                                if (chatResponse.getResult() != null && chatResponse.getResult().getOutput() != null) {
                                    chunk = chatResponse.getResult().getOutput().getText();
                                    if (chunk == null) chunk = "";
                                }
                                fullResponse.append(chunk);
                                sendChunk(emitter, chunk);

                                // 累计 token 用量（最后一个 chunk 通常包含完整 usage）
                                if (chatResponse.getMetadata() != null && chatResponse.getMetadata().getUsage() != null) {
                                    var usage = chatResponse.getMetadata().getUsage();
                                    if (usage.getPromptTokens() > 0) {
                                        totalPromptTokens.set((int) usage.getPromptTokens());
                                    }
                                    if (usage.getCompletionTokens() > 0) {
                                        totalCompletionTokens.set((int) usage.getCompletionTokens());
                                    }
                                }
                            })
                            .doOnComplete(() -> {
                                metricsCollector.decrementActive();
                                long latency = System.currentTimeMillis() - startTime;
                                String resp = fullResponse.toString();
                                log.info("[Stream] 模型输出 agent={}, userId={}, latency={}ms, chunks={}, responseLength={}, promptTokens={}, completionTokens={}, response={}",
                                        agentType, userId, latency, chunkCount.get(),
                                        resp.length(), totalPromptTokens.get(), totalCompletionTokens.get(),
                                        truncate(resp, 500));
                                int actual = totalPromptTokens.get() + totalCompletionTokens.get();
                                metricsCollector.recordRequest(null, agentType, latency, true, totalPromptTokens.get(), totalCompletionTokens.get());
                                accessChecker.recordActualTokens(userId, actual, PRE_DEDUCT_TOKENS);
                                saveAuditLog(userId, sessionId, agentType, message, resp, latency, true, null,
                                        totalPromptTokens.get(), totalCompletionTokens.get());
                                sendDone(emitter);
                            })
                            .doOnError(e -> {
                                metricsCollector.decrementActive();
                                long latency = System.currentTimeMillis() - startTime;
                                log.error("[Stream] 流式异常 agent={}, userId={}, latency={}ms, chunks={}, partialResponse={}, error={}",
                                        agentType, userId, latency, chunkCount.get(),
                                        truncate(fullResponse.toString(), 200), e.getMessage(), e);
                                metricsCollector.recordRequest(null, agentType, latency, false, 0, 0);
                                accessChecker.recordActualTokens(userId, 0, PRE_DEDUCT_TOKENS);
                                saveAuditLog(userId, sessionId, agentType, message, null, latency, false, e.getMessage(), 0, 0);
                                memoryService.rollbackLastUserMessage(sessionId);
                                sendChunk(emitter, "\n\n[AI 服务异常，请稍后重试]");
                                sendDone(emitter);
                            })
                            .subscribe();
                }
            } catch (Exception e) {
                metricsCollector.decrementActive();
                long latency = System.currentTimeMillis() - startTime;
                log.error("[Stream] 执行异常 agent={}, userId={}, latency={}ms, error={}",
                        agentType, userId, latency, e.getMessage(), e);
                metricsCollector.recordRequest(null, agentType, latency, false, 0, 0);
                accessChecker.recordActualTokens(userId, 0, PRE_DEDUCT_TOKENS);
                if (!"multi".equals(agentType)) {
                    memoryService.rollbackLastUserMessage(sessionId);
                }
                sendChunk(emitter, "\n\n[AI 服务异常，请稍后重试]");
                sendDone(emitter);
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
            @RequestHeader(value = "X-Session-Id", defaultValue = "default") String sessionId,
            HttpServletRequest request) {

        String userId = getUserId(request);
        String roles = getRoles(request);
        String department = getDepartment(request);

        String deny = accessChecker.checkPermission("multi", roles, department);
        if (deny != null) {
            return Result.fail(403, deny);
        }

        String task = body.get("task");
        if (task == null || task.isBlank()) return Result.fail(400, "task 不能为空");

        log.info("[Multi] 收到任务 userId={}, taskLength={}", userId, task.length());
        log.info("[Multi] 用户输入 userId={}, task={}", userId, truncate(task, 500));
        long startTime = System.currentTimeMillis();
        try {
            var result = multiAgentOrchestrator.executeComplexTask(userId, sessionId, task);
            long latency = System.currentTimeMillis() - startTime;
            log.info("[Multi] 模型输出 userId={}, latency={}ms, responseLength={}, tokens={}/{}, response={}",
                    userId, latency,
                    result.getContent() != null ? result.getContent().length() : 0,
                    result.getPromptTokens(), result.getCompletionTokens(),
                    truncate(result.getContent(), 500));
            return Result.ok(result.getContent());
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
            @RequestHeader(value = "X-Session-Id", defaultValue = "default") String sessionId,
            HttpServletRequest request) {
        String userId = getUserId(request);
        log.info("[Memory] 清除记忆 agent={}, userId={}, sessionId={}", agentType, userId, sessionId);
        memoryService.clearMemory(sessionId);
        return Result.ok("会话记忆已清除");
    }

    /** 查询会话历史 */
    @GetMapping("/{agentType}/memory")
    public Result<List<Map<String, String>>> getHistory(
            @PathVariable(name = "agentType") String agentType,
            @RequestHeader(value = "X-Session-Id", defaultValue = "default") String sessionId,
            HttpServletRequest request) {
        String userId = getUserId(request);
        log.info("[Memory] 查询历史 agent={}, userId={}, sessionId={}", agentType, userId, sessionId);
        return Result.ok(memoryService.getHistory(sessionId));
    }

    /** 查询当前用户在该助手下的所有会话列表 */
    @GetMapping("/{agentType}/sessions")
    public Result<List<Map<String, String>>> listSessions(
            @PathVariable(name = "agentType") String agentType,
            HttpServletRequest request) {
        String userId = getUserId(request);
        String prefix = userId + "-" + agentType + "-";
        log.info("[Sessions] 查询会话列表 agent={}, userId={}, prefix={}", agentType, userId, prefix);
        return Result.ok(memoryService.listSessions(prefix));
    }

    // ===== 从 JWT 属性中提取用户信息（由 JwtAuthFilter 设置） =====

    private String getUserId(HttpServletRequest request) {
        Object attr = request.getAttribute("X-User-Id");
        return attr != null ? attr.toString() : "anonymous";
    }

    private String getRoles(HttpServletRequest request) {
        Object attr = request.getAttribute("X-Roles");
        return attr != null ? attr.toString() : null;
    }

    private String getDepartment(HttpServletRequest request) {
        Object attr = request.getAttribute("X-Department");
        return attr != null ? attr.toString() : null;
    }

    // ===== Internal =====

    private AgentChatResult routeToAgent(String type, String userId, String sessionId, String msg) {
        return switch (type) {
            case "rd"            -> rdAssistant.chat(userId, sessionId, msg);
            case "sales"         -> salesAssistant.chat(userId, sessionId, msg);
            case "hr"            -> hrAssistant.chat(userId, sessionId, msg);
            case "finance"       -> financeAssistant.chat(userId, sessionId, msg);
            case "supply-chain"  -> supplyChainAgent.chat(userId, sessionId, msg);
            case "qc"            -> qcAssistant.chat(userId, sessionId, msg);
            case "weather"       -> weatherAssistant.chat(userId, sessionId, msg);
            case "search"        -> searchAssistant.chat(userId, sessionId, msg);
            case "data-analysis" -> dataAnalysisAssistant.chat(userId, sessionId, msg);
            case "code"          -> codeAssistant.chat(userId, sessionId, msg);
            case "mcp"           -> {
                if (mcpAssistant == null) throw new IllegalArgumentException("MCP 未启用，请在配置中开启 spring.ai.mcp.client.enabled=true");
                yield mcpAssistant.chat(userId, sessionId, msg);
            }
            case "multi"         -> {
                var result = multiAgentOrchestrator.executeComplexTask(userId, sessionId, msg);
                yield new AgentChatResult(result.getContent(), result.getPromptTokens(), result.getCompletionTokens());
            }
            default              -> throw new IllegalArgumentException("未知 Agent: " + type);
        };
    }

    private Flux<ChatResponse> routeToAgentStream(String type, String userId, String sessionId, String msg) {
        return switch (type) {
            case "rd"            -> rdAssistant.chatStream(userId, sessionId, msg);
            case "sales"         -> salesAssistant.chatStream(userId, sessionId, msg);
            case "hr"            -> hrAssistant.chatStream(userId, sessionId, msg);
            case "finance"       -> financeAssistant.chatStream(userId, sessionId, msg);
            case "supply-chain"  -> supplyChainAgent.chatStream(userId, sessionId, msg);
            case "qc"            -> qcAssistant.chatStream(userId, sessionId, msg);
            case "weather"       -> weatherAssistant.chatStream(userId, sessionId, msg);
            case "search"        -> searchAssistant.chatStream(userId, sessionId, msg);
            case "data-analysis" -> dataAnalysisAssistant.chatStream(userId, sessionId, msg);
            case "code"          -> codeAssistant.chatStream(userId, sessionId, msg);
            case "mcp"           -> {
                if (mcpAssistant == null) throw new IllegalArgumentException("MCP 未启用");
                yield mcpAssistant.chatStream(userId, sessionId, msg);
            }
            default              -> throw new IllegalArgumentException("未知 Agent: " + type);
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

    private void saveAuditLog(String userId, String sessionId, String agentType,
                              String userMessage, String aiResponse, long latencyMs,
                              boolean success, String errorMessage,
                              int promptTokens, int completionTokens) {
        try {
            auditLogMapper.insert(AiAuditLog.builder()
                    .id(UUID.randomUUID().toString())
                    .userId(userId)
                    .sessionId(sessionId)
                    .agentType(agentType)
                    .userMessage(userMessage != null && userMessage.length() > 500 ? userMessage.substring(0, 500) : userMessage)
                    .aiResponse(aiResponse != null && aiResponse.length() > 500 ? aiResponse.substring(0, 500) : aiResponse)
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .latencyMs(latencyMs)
                    .success(success)
                    .errorMessage(errorMessage)
                    .createdAt(LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            log.warn("审计日志写入失败: {}", e.getMessage());
        }
    }

}
