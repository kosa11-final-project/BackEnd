package com.stockit.backend.feature.strategy.dto.request;

import java.util.Set;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;

public final class StrategyExecutionQueryParameterValidator {

    private static final Set<String> ALLOWED_PARAMETERS = Set.of(
            "page", "size", "query", "status", "actionType", "sort"
    );

    private StrategyExecutionQueryParameterValidator() {
    }

    public static void validate(HttpServletRequest request) {
        if (request.getParameterMap().keySet().stream().anyMatch(name -> !ALLOWED_PARAMETERS.contains(name))) {
            throw new AppException(ErrorCode.INVALID_PARAMETER);
        }
    }
}
