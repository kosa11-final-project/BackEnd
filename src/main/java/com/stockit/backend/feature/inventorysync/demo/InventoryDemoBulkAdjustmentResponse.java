package com.stockit.backend.feature.inventorysync.demo;

import java.time.Instant;
import java.util.List;

public record InventoryDemoBulkAdjustmentResponse(
        String requestId,
        String status,
        int appliedCount,
        int alreadyPendingCount,
        Instant appliedAt,
        List<SourceResult> sources
) {
    public InventoryDemoBulkAdjustmentResponse {
        sources = List.copyOf(sources == null ? List.of() : sources);
    }

    public record SourceResult(
            String sourceType,
            long currentRecordCount,
            long alreadyPendingCount,
            int newlyAdjustedCount
    ) { }
}
