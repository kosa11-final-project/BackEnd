package com.stockit.backend.feature.inventory.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record InventorySummaryResponse(
        BigDecimal totalCurrentQuantity,
        BigDecimal totalAvailableQuantity,
        BigDecimal totalReservedQuantity,
        long underSafetyCount,
        long dangerRiskCount,
        long cautionRiskCount,
        long safeRiskCount,
        Instant lastSyncTime
) {
}
