package com.huah.ai.platform.common.idempotent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an endpoint as idempotent. Duplicate requests with the same
 * {@code X-Request-Id} header within {@link #ttlSeconds()} are rejected with 409 Conflict.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

    /**
     * Time-to-live in seconds for the dedup key. Default 10s.
     */
    int ttlSeconds() default 10;
}
