package com.stockit.backend.feature.statistics.dto.response;

import java.time.LocalDate;

public record InventoryStatisticsDemoBackfillResponse(
        LocalDate fromDate,
        LocalDate toDate,
        int requestedDateCount,
        int createdDateCount,
        int skippedDateCount,
        int createdSnapshotCount
) {
}
