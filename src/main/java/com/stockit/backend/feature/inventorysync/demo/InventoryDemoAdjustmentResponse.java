package com.stockit.backend.feature.inventorysync.demo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record InventoryDemoAdjustmentResponse(String requestId, String status, int appliedCount, Instant appliedAt, List<ItemResult> items) {
    public record ItemResult(String sourceType, String sourceRecordKey, BigDecimal decreaseQty, BigDecimal remainingQty) { }
    public InventoryDemoAdjustmentResponse {
        items = List.copyOf(items == null ? List.of() : items);
    }
}
