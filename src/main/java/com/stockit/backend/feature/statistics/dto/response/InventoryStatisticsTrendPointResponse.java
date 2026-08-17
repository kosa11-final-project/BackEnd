package com.stockit.backend.feature.statistics.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "동기화 완료 시점별 위험재고 추이")
public record InventoryStatisticsTrendPointResponse(
        @Schema(description = "통계 기준일", example = "2026-08-16")
        LocalDate date,
        @Schema(description = "위험 SKU 수", example = "1297")
        long criticalSkuCount,
        @Schema(description = "위험재고 수량", example = "182430")
        BigDecimal criticalStockQty
) {
}
