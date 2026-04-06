package com.huah.ai.platform.agent.controller;

public final class AgentApiConstants {

    public static final String BASE_PATH = "/api/v1/agent";
    public static final String DEFAULT_SESSION_ID = "default";
    public static final String HEADER_SESSION_ID = "X-Session-Id";
    public static final String AGENT_TYPE_MULTI = "multi";
    public static final int PRE_DEDUCT_TOKENS = 500;
    public static final int HTTP_BAD_REQUEST = 400;
    public static final int HTTP_FORBIDDEN = 403;
    public static final int HTTP_TOO_MANY_REQUESTS = 429;
    public static final int HTTP_INTERNAL_SERVER_ERROR = 500;
    public static final String MESSAGE_MESSAGE_REQUIRED = "message must not be blank";
    public static final String MESSAGE_TASK_REQUIRED = "task must not be blank";
    public static final String MESSAGE_TITLE_REQUIRED = "title must not be blank";
    public static final String MESSAGE_AI_UNAVAILABLE = "AI service is temporarily unavailable. Please retry later.";
    public static final String MESSAGE_STREAM_PERMISSION_DENIED = "[permission denied] ";
    public static final String MESSAGE_STREAM_QUOTA_DENIED = "[quota exceeded] ";
    public static final String MESSAGE_STREAM_RUNTIME_ISOLATION = "[runtime isolation] ";
    public static final String MESSAGE_STREAM_STAGE_RUNNING = " running...";
    public static final String MESSAGE_STREAM_AI_UNAVAILABLE = "\n\n[AI service is temporarily unavailable. Please retry later.]";
    public static final String MESSAGE_SESSION_ACCESS_DENIED = "You do not have permission to access this session";
    public static final String MESSAGE_SESSION_CONFIG_ACCESS_DENIED =
            "You do not have permission to access this session configuration";
    public static final String MESSAGE_SESSION_CONFIG_UPDATED = "Session configuration updated";
    public static final String MESSAGE_SESSION_TITLE_ACCESS_DENIED = "You do not have permission to update this session";
    public static final String MESSAGE_MULTI_TRACE_NOT_FOUND = "Multi-agent execution trace not found";
    public static final String MESSAGE_ARCHIVED_TRACE_NOT_FOUND = "Archived trace not found";
    public static final String MESSAGE_MEMORY_CLEARED = "Session memory cleared";
    public static final String MESSAGE_SESSION_RENAMED = "Session title updated";
    public static final String MESSAGE_SESSION_PINNED = "Session pinned";
    public static final String MESSAGE_SESSION_UNPINNED = "Session unpinned";
    public static final String MESSAGE_SESSION_ARCHIVED = "Session archived";
    public static final String MESSAGE_SESSION_UNARCHIVED = "Session unarchived";
    public static final String MESSAGE_SESSION_DELETED = "Session deleted";
    public static final String MESSAGE_FEEDBACK_SUBMITTED = "Feedback submitted";
    public static final String PHASE_AUTH_LABEL = "Auth and context";
    public static final String PHASE_PREPARATION_LABEL = "Request preparation";
    public static final String PHASE_TOOLS_LABEL = "Tool execution";
    public static final String PHASE_GENERATION_LABEL = "Model generation";
    public static final String PHASE_PERSISTENCE_LABEL = "Persistence and audit";
    public static final String PHASE_AUTH_DESCRIPTION = "Request entry, authorization checks, and quota validation.";
    public static final String PHASE_PREPARATION_DESCRIPTION =
            "Session configuration loading, prompt assembly, and model request setup.";
    public static final String PHASE_TOOLS_DESCRIPTION = "Measured time spent in tool execution based on audit records.";
    public static final String PHASE_GENERATION_DESCRIPTION = "Model inference and response generation.";
    public static final String PHASE_PERSISTENCE_DESCRIPTION = "Audit log persistence and response finalization.";

    private AgentApiConstants() {
    }
}
