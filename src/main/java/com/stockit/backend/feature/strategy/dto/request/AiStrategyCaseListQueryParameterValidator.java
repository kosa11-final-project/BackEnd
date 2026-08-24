package com.stockit.backend.feature.strategy.dto.request;

import java.util.Set;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;

public final class AiStrategyCaseListQueryParameterValidator {

    private static final Set<String> ALLOWED_PARAMETERS = Set.of(
            "page", "size", "query", "status", "from", "to", "sort"
    );

    private AiStrategyCaseListQueryParameterValidator() {
    }

    public static void validate(HttpServletRequest request) {
        boolean invalid = request.getParameterMap().entrySet().stream()
                .anyMatch(entry -> !ALLOWED_PARAMETERS.contains(entry.getKey())
                        || entry.getValue().length != 1);
        if (invalid) {
            throw new AppException(ErrorCode.INVALID_PARAMETER);
        }
    }
}
