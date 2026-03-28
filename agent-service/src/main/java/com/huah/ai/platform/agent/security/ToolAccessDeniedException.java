package com.huah.ai.platform.agent.security;

public class ToolAccessDeniedException extends IllegalStateException {

    private final String reasonCode;
    private final String resource;
    private final String detail;

    public ToolAccessDeniedException(String reasonCode, String reasonMessage, String resource) {
        this(reasonCode, reasonMessage, resource, null);
    }

    public ToolAccessDeniedException(String reasonCode, String reasonMessage, String resource, String detail) {
        super(reasonMessage);
        this.reasonCode = reasonCode;
        this.resource = resource;
        this.detail = detail;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public String getResource() {
        return resource;
    }

    public String getDetail() {
        return detail;
    }
}
