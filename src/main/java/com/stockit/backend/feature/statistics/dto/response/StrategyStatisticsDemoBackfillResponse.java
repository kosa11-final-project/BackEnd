package com.stockit.backend.feature.statistics.dto.response;

import java.time.LocalDate;

public record StrategyStatisticsDemoBackfillResponse(
        LocalDate fromDate,
        LocalDate toDate,
        int requestedStrategyCount,
        int createdStrategyCount,
        int skippedStrategyCount,
        int createdActionCount
) {
}
