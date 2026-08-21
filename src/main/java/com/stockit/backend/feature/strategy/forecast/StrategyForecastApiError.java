package com.stockit.backend.feature.strategy.forecast;

import com.fasterxml.jackson.databind.JsonNode;

public record StrategyForecastApiError(
        String code,
        String message,
        JsonNode details
) {
}
