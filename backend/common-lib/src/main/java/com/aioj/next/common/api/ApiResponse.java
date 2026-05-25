package com.aioj.next.common.api;

import java.time.Instant;

public record ApiResponse<T>(int code, String message, T data, Object details, String traceId, Instant timestamp) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data, null, TraceIds.current(), Instant.now());
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(code, message, null, null, TraceIds.current(), Instant.now());
    }

    public static <T> ApiResponse<T> failWithDetails(int code, String message, Object details) {
        return new ApiResponse<>(code, message, null, details, TraceIds.current(), Instant.now());
    }
}
