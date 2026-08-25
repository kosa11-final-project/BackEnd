package com.stockit.backend.feature.strategy.forecast;

import java.util.List;

public record StrategyForecastRequestContext(
        StrategyForecastRequest request,
        List<Long> expectedSalesPointIds,
        String requestHash
) {

    public StrategyForecastRequestContext {
        expectedSalesPointIds = List.copyOf(expectedSalesPointIds);
    }
}
