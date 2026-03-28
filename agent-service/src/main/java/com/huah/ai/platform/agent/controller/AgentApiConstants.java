package com.huah.ai.platform.agent.controller;

public final class AgentApiConstants {

    static final String BASE_PATH = "/api/v1/agent";
    static final String DEFAULT_SESSION_ID = "default";
    static final String HEADER_SESSION_ID = "X-Session-Id";
    static final String AGENT_TYPE_MULTI = "multi";
    static final int PRE_DEDUCT_TOKENS = 500;
    static final int HTTP_BAD_REQUEST = 400;
    static final int HTTP_FORBIDDEN = 403;
    static final int HTTP_TOO_MANY_REQUESTS = 429;
    static final int HTTP_INTERNAL_SERVER_ERROR = 500;
    static final String MESSAGE_MESSAGE_REQUIRED = "message must not be blank";
    static final String MESSAGE_TASK_REQUIRED = "task must not be blank";
    static final String MESSAGE_TITLE_REQUIRED = "title must not be blank";
    static final String MESSAGE_AI_UNAVAILABLE = "AI service is temporarily unavailable. Please retry later.";
    static final String MESSAGE_STREAM_PERMISSION_DENIED = "[permission denied] ";
    static final String MESSAGE_STREAM_QUOTA_DENIED = "[quota exceeded] ";
    static final String MESSAGE_STREAM_RUNTIME_ISOLATION = "[runtime isolation] ";
    static final String MESSAGE_STREAM_STAGE_RUNNING = " running...";
    static final String MESSAGE_STREAM_AI_UNAVAILABLE = "\n\n[AI service is temporarily unavailable. Please retry later.]";
    static final String MESSAGE_SESSION_ACCESS_DENIED = "You do not have permission to access this session";
    static final String MESSAGE_SESSION_CONFIG_ACCESS_DENIED =
            "You do not have permission to access this session configuration";
    static final String MESSAGE_SESSION_CONFIG_UPDATED = "Session configuration updated";
    static final String MESSAGE_SESSION_TITLE_ACCESS_DENIED = "You do not have permission to update this session";
    static final String MESSAGE_MULTI_TRACE_NOT_FOUND = "Multi-agent execution trace not found";
    static final String MESSAGE_ARCHIVED_TRACE_NOT_FOUND = "Archived trace not found";
    static final String MESSAGE_MEMORY_CLEARED = "Session memory cleared";
    static final String MESSAGE_SESSION_RENAMED = "Session title updated";
    static final String MESSAGE_SESSION_PINNED = "Session pinned";
    static final String MESSAGE_SESSION_UNPINNED = "Session unpinned";
    static final String MESSAGE_SESSION_ARCHIVED = "Session archived";
    static final String MESSAGE_SESSION_UNARCHIVED = "Session unarchived";
    static final String MESSAGE_SESSION_DELETED = "Session deleted";
    static final String MESSAGE_FEEDBACK_SUBMITTED = "Feedback submitted";
    static final String PHASE_AUTH_LABEL = "Auth and context";
    static final String PHASE_PREPARATION_LABEL = "Request preparation";
    static final String PHASE_TOOLS_LABEL = "Tool execution";
    static final String PHASE_GENERATION_LABEL = "Model generation";
    static final String PHASE_PERSISTENCE_LABEL = "Persistence and audit";
    static final String PHASE_AUTH_DESCRIPTION = "Request entry, authorization checks, and quota validation.";
    static final String PHASE_PREPARATION_DESCRIPTION =
            "Session configuration loading, prompt assembly, and model request setup.";
    static final String PHASE_TOOLS_DESCRIPTION = "Measured time spent in tool execution based on audit records.";
    static final String PHASE_GENERATION_DESCRIPTION = "Model inference and response generation.";
    static final String PHASE_PERSISTENCE_DESCRIPTION = "Audit log persistence and response finalization.";

    private AgentApiConstants() {
    }
}
