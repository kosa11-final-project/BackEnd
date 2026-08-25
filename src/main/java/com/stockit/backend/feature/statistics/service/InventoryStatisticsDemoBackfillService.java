package com.stockit.backend.feature.statistics.service;

import java.time.LocalDate;

import com.stockit.backend.feature.statistics.dto.response.InventoryStatisticsDemoBackfillResponse;

public interface InventoryStatisticsDemoBackfillService {
    InventoryStatisticsDemoBackfillResponse backfill(LocalDate fromDate, LocalDate toDate);
}
