package com.huah.ai.platform.agent.tools;

import com.huah.ai.platform.agent.security.ToolAccessDeniedException;

import java.util.LinkedHashMap;
import java.util.Map;

final class ToolResponseSupport {

    private ToolResponseSupport() {
    }

    static Map<String, Object> error(String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("error", message);
        return result;
    }

    static Map<String, Object> error(String message, String errorCode) {
        Map<String, Object> result = error(message);
        result.put("errorCode", errorCode);
        return result;
    }

    static Map<String, Object> accessDenied(ToolAccessDeniedException exception) {
        Map<String, Object> result = error(exception.getMessage(), exception.getReasonCode());
        result.put("resource", exception.getResource());
        result.put("detail", exception.getDetail());
        return result;
    }
}
