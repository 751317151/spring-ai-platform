package com.huah.ai.platform.agent.controller;

import com.huah.ai.platform.agent.dto.AgentChatRequest;
import com.huah.ai.platform.agent.dto.AgentChatResponse;
import com.huah.ai.platform.agent.dto.MultiAgentTaskRequest;
import com.huah.ai.platform.agent.memory.ConversationMemoryService;
import com.huah.ai.platform.agent.security.AgentAccessChecker;
import com.huah.ai.platform.agent.service.AgentConversationOrchestrator;
import com.huah.ai.platform.agent.service.AgentRuntimeIsolationService;
import com.huah.ai.platform.common.dto.Result;
import com.huah.ai.platform.common.web.RequestOrigin;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping(AgentApiConstants.BASE_PATH)
@RequiredArgsConstructor
public class AgentConversationController {

    private final ConversationMemoryService memoryService;
    private final AgentAccessChecker accessChecker;
    private final AgentControllerSupport controllerSupport;
    private final AgentRuntimeIsolationService agentRuntimeIsolationService;
    private final AgentConversationOrchestrator conversationOrchestrator;

    @PostMapping("/{agentType}/chat")
    public Result<AgentChatResponse> chat(
            @PathVariable(name = "agentType") String agentType,
            @RequestBody AgentChatRequest body,
            @RequestHeader(value = AgentApiConstants.HEADER_SESSION_ID,
                    defaultValue = AgentApiConstants.DEFAULT_SESSION_ID) String sessionId,
            HttpServletRequest request) {
        AgentRequestContext context = controllerSupport.resolveContext(request);
        RequestOrigin requestOrigin = controllerSupport.resolveOrigin(request);
        String userId = context.getUserId();

        Result<AgentChatResponse> accessFailure = validateChatAccess(agentType, context, userId);
        if (accessFailure != null) {
            return accessFailure;
        }

        AgentRuntimeIsolationService.RuntimeIsolationDecision isolationDecision =
                agentRuntimeIsolationService.acquire(agentType);
        if (!isolationDecision.allowed()) {
            return Result.fail(AgentApiConstants.HTTP_TOO_MANY_REQUESTS, isolationDecision.reasonMessage());
        }

        String message = prepareMessage(body, sessionId);
        if (message == null) {
            return Result.fail(AgentApiConstants.HTTP_BAD_REQUEST, AgentApiConstants.MESSAGE_MESSAGE_REQUIRED);
        }

        log.info("[Chat] received agent={}, userId={}, sessionId={}, messageLength={}",
                agentType, userId, sessionId, message.length());
        log.info("[Chat] input agent={}, userId={}, message={}",
                agentType, userId, controllerSupport.truncate(message, 500));

        try {
            return Result.ok(conversationOrchestrator.executeChat(agentType, userId, sessionId, message, requestOrigin));
        } catch (Exception e) {
            return Result.fail(AgentApiConstants.HTTP_INTERNAL_SERVER_ERROR, AgentApiConstants.MESSAGE_AI_UNAVAILABLE);
        } finally {
            agentRuntimeIsolationService.release(agentType);
        }
    }

    @PostMapping(value = "/{agentType}/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(
            @PathVariable(name = "agentType") String agentType,
            @RequestBody AgentChatRequest body,
            @RequestHeader(value = AgentApiConstants.HEADER_SESSION_ID,
                    defaultValue = AgentApiConstants.DEFAULT_SESSION_ID) String sessionId,
            HttpServletRequest request) {
        long streamTimeoutMs = agentRuntimeIsolationService.getStreamTimeoutMs(agentType, 180_000L);
        SseEmitter emitter = new SseEmitter(streamTimeoutMs);
        AgentRequestContext context = controllerSupport.resolveContext(request);
        RequestOrigin requestOrigin = controllerSupport.resolveOrigin(request);
        String userId = context.getUserId();

        String accessFailure = validateStreamAccess(agentType, context, userId);
        if (accessFailure != null) {
            sendChunk(emitter, accessFailure);
            sendDone(emitter, null, null);
            return emitter;
        }

        String message = prepareMessage(body, sessionId);
        if (message == null) {
            log.warn("[Stream] empty message, agent={}, userId={}", agentType, userId);
            sendChunk(emitter, AgentApiConstants.MESSAGE_MESSAGE_REQUIRED);
            sendDone(emitter, null, null);
            return emitter;
        }

        AgentRuntimeIsolationService.RuntimeIsolationDecision streamIsolationDecision =
                agentRuntimeIsolationService.acquire(agentType);
        if (!streamIsolationDecision.allowed()) {
            sendChunk(emitter, AgentApiConstants.MESSAGE_STREAM_RUNTIME_ISOLATION
                    + streamIsolationDecision.reasonMessage());
            sendDone(emitter, null, null);
            return emitter;
        }

        log.info("[Stream] received agent={}, userId={}, sessionId={}, messageLength={}",
                agentType, userId, sessionId, message.length());
        log.info("[Stream] input agent={}, userId={}, message={}",
                agentType, userId, controllerSupport.truncate(message, 500));

        conversationOrchestrator.executeStream(agentType, userId, sessionId, message, requestOrigin, emitter);

        emitter.onTimeout(() -> {
            log.warn("[Stream] timeout agent={}, userId={}", agentType, userId);
            agentRuntimeIsolationService.release(agentType);
        });
        emitter.onCompletion(() -> agentRuntimeIsolationService.release(agentType));
        return emitter;
    }

    @PostMapping("/multi/execute")
    public Result<String> multiAgentExecute(
            @RequestBody MultiAgentTaskRequest body,
            @RequestHeader(value = AgentApiConstants.HEADER_SESSION_ID,
                    defaultValue = AgentApiConstants.DEFAULT_SESSION_ID) String sessionId,
            HttpServletRequest request) {
        AgentRequestContext context = controllerSupport.resolveContext(request);
        String userId = context.getUserId();

        String deny = accessChecker.checkPermission(
                AgentApiConstants.AGENT_TYPE_MULTI, context.getRoles(), context.getDepartment());
        if (deny != null) {
            return Result.fail(AgentApiConstants.HTTP_FORBIDDEN, deny);
        }

        String task = body.getTask();
        if (task == null || task.isBlank()) {
            return Result.fail(AgentApiConstants.HTTP_BAD_REQUEST, AgentApiConstants.MESSAGE_TASK_REQUIRED);
        }

        log.info("[Multi] received userId={}, taskLength={}", userId, task.length());
        log.info("[Multi] input userId={}, task={}", userId, controllerSupport.truncate(task, 500));

        return Result.ok(conversationOrchestrator.executeMultiTask(userId, sessionId, task));
    }

    private Result<AgentChatResponse> validateChatAccess(String agentType, AgentRequestContext context, String userId) {
        String deny = accessChecker.checkPermission(agentType, context.getRoles(), context.getDepartment());
        if (deny != null) {
            log.warn("[Chat] permission denied agent={}, userId={}, roles={}, reason={}",
                    agentType, userId, context.getRoles(), deny);
            return Result.fail(AgentApiConstants.HTTP_FORBIDDEN, deny);
        }

        String quotaDeny = accessChecker.checkAndConsumeTokens(userId, agentType, AgentApiConstants.PRE_DEDUCT_TOKENS);
        if (quotaDeny != null) {
            log.warn("[Chat] token quota exceeded agent={}, userId={}", agentType, userId);
            return Result.fail(AgentApiConstants.HTTP_TOO_MANY_REQUESTS, quotaDeny);
        }
        return null;
    }

    private String validateStreamAccess(String agentType, AgentRequestContext context, String userId) {
        String deny = accessChecker.checkPermission(agentType, context.getRoles(), context.getDepartment());
        if (deny != null) {
            log.warn("[Stream] permission denied agent={}, userId={}, roles={}, reason={}",
                    agentType, userId, context.getRoles(), deny);
            return AgentApiConstants.MESSAGE_STREAM_PERMISSION_DENIED + deny;
        }

        String quotaDeny = accessChecker.checkAndConsumeTokens(userId, agentType, AgentApiConstants.PRE_DEDUCT_TOKENS);
        if (quotaDeny != null) {
            log.warn("[Stream] token quota exceeded agent={}, userId={}", agentType, userId);
            return AgentApiConstants.MESSAGE_STREAM_QUOTA_DENIED + quotaDeny;
        }
        return null;
    }

    private String prepareMessage(AgentChatRequest body, String sessionId) {
        String message = body.getMessage();
        if (message == null || message.isBlank()) {
            return null;
        }
        if (body.getSessionConfig() != null) {
            memoryService.saveSessionConfig(sessionId, body.getSessionConfig());
        }
        return message;
    }

    private void sendChunk(SseEmitter emitter, String chunk) {
        try {
            emitter.send(SseEmitter.event().data(Map.of("chunk", chunk, "done", false)));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    private void sendDone(SseEmitter emitter, Long responseId, String traceId) {
        try {
            emitter.send(SseEmitter.event().data(Map.of(
                    "chunk", "",
                    "done", true,
                    "responseId", responseId == null ? "" : String.valueOf(responseId),
                    "traceId", traceId == null ? "" : traceId
            )));
            emitter.complete();
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

}
