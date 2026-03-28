package com.huah.ai.platform.common.exception;

import com.huah.ai.platform.common.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final int HTTP_BAD_REQUEST = 400;
    private static final int HTTP_FORBIDDEN = 403;
    private static final int HTTP_SERVICE_UNAVAILABLE = 503;
    private static final int HTTP_INTERNAL_SERVER_ERROR = 500;
    private static final String MESSAGE_AI_SERVICE_UNAVAILABLE = "AI service is temporarily unavailable";
    private static final String MESSAGE_ACCESS_DENIED = "You do not have permission to access this resource";
    private static final String MESSAGE_VALIDATION_FAILED = "Request validation failed";
    private static final String MESSAGE_INTERNAL_SERVER_ERROR = "Internal server error";

    @ExceptionHandler(BizException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBizException(BizException exception) {
        log.warn("Business exception: code={}, message={}", exception.getCode(), exception.getMessage());
        return Result.fail(exception.getCode(), exception.getMessage());
    }

    @ExceptionHandler(AiServiceException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Result<Void> handleAiServiceException(AiServiceException exception) {
        log.error("AI service exception: {}", exception.getMessage(), exception);
        return Result.fail(HTTP_SERVICE_UNAVAILABLE, MESSAGE_AI_SERVICE_UNAVAILABLE + ": " + exception.getMessage());
    }

    @ExceptionHandler(PermissionDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handlePermissionDenied(PermissionDeniedException exception) {
        log.warn("Permission denied: {}", exception.getMessage());
        return Result.fail(HTTP_FORBIDDEN, exception.getMessage());
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleAccessDenied(Exception exception) {
        log.warn("Access denied: {}", exception.getMessage());
        return Result.fail(HTTP_FORBIDDEN, MESSAGE_ACCESS_DENIED);
    }

    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidation(BindException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(field -> field.getField() + ": " + field.getDefaultMessage())
                .findFirst()
                .orElse(MESSAGE_VALIDATION_FAILED);
        return Result.fail(HTTP_BAD_REQUEST, message);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception exception) {
        log.error("Unhandled exception", exception);
        return Result.fail(HTTP_INTERNAL_SERVER_ERROR, MESSAGE_INTERNAL_SERVER_ERROR);
    }
}
