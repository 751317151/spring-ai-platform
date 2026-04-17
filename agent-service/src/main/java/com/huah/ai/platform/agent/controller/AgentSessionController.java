package com.huah.ai.platform.agent.controller;

import com.huah.ai.platform.agent.audit.ResponseFeedbackService;
import com.huah.ai.platform.agent.dto.ResponseFeedbackRequest;
import com.huah.ai.platform.agent.dto.SessionConfigRequest;
import com.huah.ai.platform.agent.dto.SessionConfigResponse;
import com.huah.ai.platform.agent.dto.SessionTitleRequest;
import com.huah.ai.platform.agent.dto.SessionToggleRequest;
import com.huah.ai.platform.agent.memory.ConversationMemoryService;
import com.huah.ai.platform.common.dto.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping(AgentApiConstants.BASE_PATH)
@RequiredArgsConstructor
@Tag(name = "Agent Session", description = "会话管理 - 记忆、历史、配置、置顶、归档")
public class AgentSessionController {

    private final ConversationMemoryService memoryService;
    private final ResponseFeedbackService feedbackService;
    private final com.huah.ai.platform.agent.security.AgentAccessChecker accessChecker;
    private final AgentControllerSupport controllerSupport;

    @Operation(summary = "清除会话记忆")
    @DeleteMapping("/{agentType}/memory")
    public Result<String> clearMemory(
            @PathVariable(name = "agentType") String agentType,
            @RequestHeader(value = AgentApiConstants.HEADER_SESSION_ID,
                    defaultValue = AgentApiConstants.DEFAULT_SESSION_ID) String sessionId,
            HttpServletRequest request) {
        String userId = controllerSupport.currentUserId(request);
        String deny = controllerSupport.checkAgentAccess(agentType, request, accessChecker, "WRITE");
        if (deny != null) {
            return Result.fail(AgentApiConstants.HTTP_FORBIDDEN, deny);
        }
        if (!controllerSupport.ownsSession(userId, agentType, sessionId)) {
            return Result.fail(AgentApiConstants.HTTP_FORBIDDEN, AgentApiConstants.MESSAGE_SESSION_ACCESS_DENIED);
        }
        log.info("[Memory] clear agent={}, userId={}, sessionId={}", agentType, userId, sessionId);
        memoryService.clearMemory(sessionId);
        return Result.ok(AgentApiConstants.MESSAGE_MEMORY_CLEARED);
    }

    @Operation(summary = "获取对话历史（支持分页）")
    @GetMapping("/{agentType}/memory")
    public Result<Map<String, Object>> getHistory(
            @PathVariable(name = "agentType") String agentType,
            @RequestHeader(value = AgentApiConstants.HEADER_SESSION_ID,
                    defaultValue = AgentApiConstants.DEFAULT_SESSION_ID) String sessionId,
            @RequestParam(name = "offset", defaultValue = "0") int offset,
            @RequestParam(name = "limit", defaultValue = "50") int limit,
            HttpServletRequest request) {
        String userId = controllerSupport.currentUserId(request);
        String deny = controllerSupport.checkAgentAccess(agentType, request, accessChecker, "READ");
        if (deny != null) {
            return Result.fail(AgentApiConstants.HTTP_FORBIDDEN, deny);
        }
        if (!controllerSupport.ownsSession(userId, agentType, sessionId)) {
            return Result.fail(AgentApiConstants.HTTP_FORBIDDEN, AgentApiConstants.MESSAGE_SESSION_ACCESS_DENIED);
        }
        int safeOffset = Math.max(0, offset);
        int safeLimit = controllerSupport.safeLimit(limit, 50, 1, 200);
        log.info("[Memory] history agent={}, userId={}, sessionId={}, offset={}, limit={}",
                agentType, userId, sessionId, safeOffset, safeLimit);
        List<Map<String, String>> messages = memoryService.getHistory(sessionId, safeOffset, safeLimit);
        int total = memoryService.getHistoryCount(sessionId);
        return Result.ok(Map.of(
                "messages", messages,
                "total", total,
                "offset", safeOffset,
                "limit", safeLimit
        ));
    }

    @Operation(summary = "获取会话列表")
    @GetMapping("/{agentType}/sessions")
    public Result<List<Map<String, String>>> listSessions(
            @PathVariable(name = "agentType") String agentType,
            @RequestHeader(value = AgentApiConstants.HEADER_SESSION_ID, required = false) String ignoredSessionId,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "includeArchived", defaultValue = "false") boolean includeArchived,
            @RequestParam(name = "pinnedOnly", defaultValue = "false") boolean pinnedOnly,
            @RequestParam(name = "updatedAfter", required = false) Long updatedAfter,
            @RequestParam(name = "updatedBefore", required = false) Long updatedBefore,
            @RequestParam(name = "limit", required = false) Integer limit,
            HttpServletRequest request) {
        String userId = controllerSupport.currentUserId(request);
        String deny = controllerSupport.checkAgentAccess(agentType, request, accessChecker, "READ");
        if (deny != null) {
            return Result.fail(AgentApiConstants.HTTP_FORBIDDEN, deny);
        }
        String prefix = controllerSupport.buildSessionPrefix(userId, agentType);
        log.info("[Sessions] list agent={}, userId={}, prefix={}", agentType, userId, prefix);
        return Result.ok(memoryService.searchSessions(
                prefix, keyword, includeArchived, pinnedOnly, updatedAfter, updatedBefore, limit));
    }

    @GetMapping("/{agentType}/sessions/config")
    public Result<SessionConfigResponse> getSessionConfig(
            @PathVariable(name = "agentType") String agentType,
            @RequestHeader(value = AgentApiConstants.HEADER_SESSION_ID) String sessionId,
            HttpServletRequest request) {
        String userId = controllerSupport.currentUserId(request);
        String deny = controllerSupport.checkAgentAccess(agentType, request, accessChecker, "READ");
        if (deny != null) {
            return Result.fail(AgentApiConstants.HTTP_FORBIDDEN, deny);
        }
        if (!controllerSupport.ownsSession(userId, agentType, sessionId)) {
            return Result.fail(
                    AgentApiConstants.HTTP_FORBIDDEN, AgentApiConstants.MESSAGE_SESSION_CONFIG_ACCESS_DENIED);
        }
        return Result.ok(memoryService.getSessionConfig(sessionId));
    }

    @PostMapping("/{agentType}/sessions/config")
    public Result<String> saveSessionConfig(
            @PathVariable(name = "agentType") String agentType,
            @RequestHeader(value = AgentApiConstants.HEADER_SESSION_ID) String sessionId,
            @RequestBody SessionConfigRequest body,
            HttpServletRequest request) {
        String userId = controllerSupport.currentUserId(request);
        String deny = controllerSupport.checkAgentAccess(agentType, request, accessChecker, "WRITE");
        if (deny != null) {
            return Result.fail(AgentApiConstants.HTTP_FORBIDDEN, deny);
        }
        if (!controllerSupport.ownsSession(userId, agentType, sessionId)) {
            return Result.fail(
                    AgentApiConstants.HTTP_FORBIDDEN, AgentApiConstants.MESSAGE_SESSION_CONFIG_ACCESS_DENIED);
        }
        memoryService.saveSessionConfig(sessionId, body);
        return Result.ok(AgentApiConstants.MESSAGE_SESSION_CONFIG_UPDATED);
    }

    @PostMapping("/{agentType}/sessions/title")
    public Result<String> renameSessionTitle(
            @PathVariable(name = "agentType") String agentType,
            @RequestHeader(value = AgentApiConstants.HEADER_SESSION_ID) String sessionId,
            @RequestBody SessionTitleRequest body,
            HttpServletRequest request) {
        String userId = controllerSupport.currentUserId(request);
        String deny = controllerSupport.checkAgentAccess(agentType, request, accessChecker, "WRITE");
        if (deny != null) {
            return Result.fail(AgentApiConstants.HTTP_FORBIDDEN, deny);
        }
        if (!controllerSupport.ownsSession(userId, agentType, sessionId)) {
            return Result.fail(
                    AgentApiConstants.HTTP_FORBIDDEN, AgentApiConstants.MESSAGE_SESSION_TITLE_ACCESS_DENIED);
        }
        String title = body.getTitle();
        if (title == null || title.isBlank()) {
            return Result.fail(AgentApiConstants.HTTP_BAD_REQUEST, AgentApiConstants.MESSAGE_TITLE_REQUIRED);
        }
        memoryService.renameSession(sessionId, title);
        return Result.ok(AgentApiConstants.MESSAGE_SESSION_RENAMED);
    }

    @PostMapping("/{agentType}/sessions/pin")
    public Result<String> pinSession(
            @PathVariable(name = "agentType") String agentType,
            @RequestHeader(value = AgentApiConstants.HEADER_SESSION_ID) String sessionId,
            @RequestBody SessionToggleRequest body,
            HttpServletRequest request) {
        String userId = controllerSupport.currentUserId(request);
        String deny = controllerSupport.checkAgentAccess(agentType, request, accessChecker, "WRITE");
        if (deny != null) {
            return Result.fail(AgentApiConstants.HTTP_FORBIDDEN, deny);
        }
        if (!controllerSupport.ownsSession(userId, agentType, sessionId)) {
            return Result.fail(
                    AgentApiConstants.HTTP_FORBIDDEN, AgentApiConstants.MESSAGE_SESSION_TITLE_ACCESS_DENIED);
        }

        boolean pinned = Boolean.TRUE.equals(body.getPinned());
        memoryService.pinSession(sessionId, pinned);
        return Result.ok(pinned ? AgentApiConstants.MESSAGE_SESSION_PINNED : AgentApiConstants.MESSAGE_SESSION_UNPINNED);
    }

    @PostMapping("/{agentType}/sessions/archive")
    public Result<String> archiveSession(
            @PathVariable(name = "agentType") String agentType,
            @RequestHeader(value = AgentApiConstants.HEADER_SESSION_ID) String sessionId,
            @RequestBody SessionToggleRequest body,
            HttpServletRequest request) {
        String userId = controllerSupport.currentUserId(request);
        String deny = controllerSupport.checkAgentAccess(agentType, request, accessChecker, "WRITE");
        if (deny != null) {
            return Result.fail(AgentApiConstants.HTTP_FORBIDDEN, deny);
        }
        if (!controllerSupport.ownsSession(userId, agentType, sessionId)) {
            return Result.fail(
                    AgentApiConstants.HTTP_FORBIDDEN, AgentApiConstants.MESSAGE_SESSION_TITLE_ACCESS_DENIED);
        }

        boolean archived = Boolean.TRUE.equals(body.getArchived());
        memoryService.archiveSession(sessionId, archived);
        return Result.ok(archived
                ? AgentApiConstants.MESSAGE_SESSION_ARCHIVED
                : AgentApiConstants.MESSAGE_SESSION_UNARCHIVED);
    }

    @DeleteMapping("/{agentType}/sessions")
    public Result<String> deleteSession(
            @PathVariable(name = "agentType") String agentType,
            @RequestHeader(value = AgentApiConstants.HEADER_SESSION_ID) String sessionId,
            HttpServletRequest request) {
        String userId = controllerSupport.currentUserId(request);
        String deny = controllerSupport.checkAgentAccess(agentType, request, accessChecker, "WRITE");
        if (deny != null) {
            return Result.fail(AgentApiConstants.HTTP_FORBIDDEN, deny);
        }
        if (!controllerSupport.ownsSession(userId, agentType, sessionId)) {
            return Result.fail(AgentApiConstants.HTTP_FORBIDDEN, AgentApiConstants.MESSAGE_SESSION_ACCESS_DENIED);
        }

        memoryService.clearMemory(sessionId);
        return Result.ok(AgentApiConstants.MESSAGE_SESSION_DELETED);
    }

    @PostMapping("/feedback")
    public Result<String> submitFeedback(@RequestBody ResponseFeedbackRequest body, HttpServletRequest request) {
        String userId = controllerSupport.currentUserId(request);
        feedbackService.submitAgentFeedback(userId, body.getResponseId(), body.getFeedback(), body.getComment());
        return Result.ok(AgentApiConstants.MESSAGE_FEEDBACK_SUBMITTED);
    }
}
