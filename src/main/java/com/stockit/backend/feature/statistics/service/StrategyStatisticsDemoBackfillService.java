package com.stockit.backend.feature.statistics.service;

import java.time.LocalDate;

import com.stockit.backend.feature.statistics.dto.response.StrategyStatisticsDemoBackfillResponse;

public interface StrategyStatisticsDemoBackfillService {
    StrategyStatisticsDemoBackfillResponse backfill(LocalDate fromDate, LocalDate toDate);
}
