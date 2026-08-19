package com.stockit.backend.feature.statistics.dto.response;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "SKU 원가 기준 재고 금액 지표")
public record InventoryFinancialStatisticsResponse(
        @Schema(description = "전체 재고 원가액", example = "4821500000")
        BigDecimal totalInventoryCostAmount,
        @Schema(description = "CRITICAL 재고 원가액", example = "393400000")
        BigDecimal criticalInventoryCostAmount,
        @Schema(description = "향후 30일 예상 폐기 손실액", example = "12750000")
        BigDecimal expectedDisposalLossAmount30d,
        @Schema(description = "유효한 원가가 없는 SKU 수", example = "12")
        long missingCostSkuCount,
        @Schema(description = "유효한 원가가 없는 재고수량", example = "4280")
        BigDecimal missingCostStockQty
) {
}
