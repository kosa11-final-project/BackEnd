package com.stockit.backend.feature.strategy.forecast;

import java.time.LocalDate;
import java.util.List;

public record StrategyForecastRequest(
        Long strategyRequestId,
        Long skuId,
        Long sourceSalesPointId,
        List<Long> candidateSalesPointIds,
        LocalDate forecastStartDate,
        LocalDate forecastEndDate
) {

    public StrategyForecastRequest {
        candidateSalesPointIds = candidateSalesPointIds == null
                ? List.of()
                : List.copyOf(candidateSalesPointIds);
    }
}
