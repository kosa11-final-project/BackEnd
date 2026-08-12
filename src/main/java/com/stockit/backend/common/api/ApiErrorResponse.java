package com.stockit.backend.common.api;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.stockit.backend.common.exception.ErrorCode;

@JsonPropertyOrder({"code", "message", "fieldErrors", "path", "timestamp"})
public record ApiErrorResponse(
        String code,
        String message,
        List<FieldErrorDetail> fieldErrors,
        String path,
        Instant timestamp
) {

    public ApiErrorResponse {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }

    public static ApiErrorResponse of(ErrorCode errorCode, String path) {
        return of(errorCode, errorCode.getMessage(), List.of(), path);
    }

    public static ApiErrorResponse of(ErrorCode errorCode, String message, String path) {
        return of(errorCode, message, List.of(), path);
    }

    public static ApiErrorResponse of(
            ErrorCode errorCode,
            String message,
            List<FieldErrorDetail> fieldErrors,
            String path
    ) {
        return new ApiErrorResponse(
                errorCode.getCode(),
                message,
                fieldErrors,
                path,
                Instant.now()
        );
    }
}
