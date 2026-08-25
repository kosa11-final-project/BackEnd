package com.stockit.backend.feature.strategy.forecast;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record StrategyForecastResponse(
        Long strategyRequestId,
        Long skuId,
        Long sourceSalesPointId,
        List<Long> requestedCandidateSalesPointIds,
        LocalDate forecastStartDate,
        LocalDate forecastEndDate,
        Integer forecastDays,
        String forecastRunId,
        String modelName,
        String modelVersion,
        OffsetDateTime forecastGeneratedAt,
        List<SalesPointForecast> salesPointForecasts
) {
}
