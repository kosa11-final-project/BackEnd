package com.stockit.backend.common.api;

import java.util.Objects;

public record FieldErrorDetail(String field, String message) {

    public FieldErrorDetail {
        Objects.requireNonNull(field, "field must not be null");
        Objects.requireNonNull(message, "message must not be null");
    }
}
