package com.stockit.backend.common.api;

import java.time.Instant;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"data", "timestamp"})
public record ApiResponse<T>(T data, Instant timestamp) {

    public ApiResponse {
        Objects.requireNonNull(timestamp, "timestamp must not be null");
    }

    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data, Instant.now());
    }

    public static ApiResponse<Void> empty() {
        return new ApiResponse<>(null, Instant.now());
    }
}
