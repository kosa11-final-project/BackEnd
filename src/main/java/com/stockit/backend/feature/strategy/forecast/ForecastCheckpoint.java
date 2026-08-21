package com.stockit.backend.feature.strategy.forecast;

import java.time.Instant;
import java.util.List;

public record ForecastCheckpoint(
        int schemaVersion,
        Long strategyCaseId,
        String requestHash,
        List<Long> expectedSalesPointIds,
        Instant storedAt,
        StrategyForecastResponse forecastResponse
) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public ForecastCheckpoint {
        expectedSalesPointIds = expectedSalesPointIds == null
                ? List.of()
                : List.copyOf(expectedSalesPointIds);
    }

    public static ForecastCheckpoint create(
            StrategyForecastRequestContext context,
            StrategyForecastResponse response,
            Instant storedAt
    ) {
        return new ForecastCheckpoint(
                CURRENT_SCHEMA_VERSION,
                context.request().strategyRequestId(),
                context.requestHash(),
                context.expectedSalesPointIds(),
                storedAt,
                response
        );
    }
}
