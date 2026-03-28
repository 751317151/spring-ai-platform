package com.huah.ai.platform.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.huah.ai.platform.common.trace.TraceIdContext;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Standard API response envelope.
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> {

    private int code;
    private String message;
    private T data;
    private Object error;
    private long timestamp;
    private String traceId;

    public static <T> Result<T> ok(T data) {
        Result<T> response = new Result<>();
        response.code = 200;
        response.message = "success";
        response.data = data;
        response.timestamp = Instant.now().toEpochMilli();
        response.traceId = TraceIdContext.currentTraceId();
        return response;
    }

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> fail(int code, String message) {
        return fail(code, message, null);
    }

    public static <T> Result<T> fail(int code, String message, Object error) {
        Result<T> response = new Result<>();
        response.code = code;
        response.message = message;
        response.error = error;
        response.timestamp = Instant.now().toEpochMilli();
        response.traceId = TraceIdContext.currentTraceId();
        return response;
    }

    public static <T> Result<T> fail(String message) {
        return fail(500, message);
    }

    public Result<T> traceId(String traceId) {
        this.traceId = traceId;
        return this;
    }
}
