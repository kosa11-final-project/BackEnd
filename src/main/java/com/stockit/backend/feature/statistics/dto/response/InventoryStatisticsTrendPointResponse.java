package com.stockit.backend.feature.statistics.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "동기화 완료 시점별 위험재고 추이")
public record InventoryStatisticsTrendPointResponse(
        @Schema(description = "통계 기준일", example = "2026-08-16")
        LocalDate date,
        @Schema(description = "전체 재고수량", example = "3790195")
        BigDecimal totalStockQty,
        @Schema(description = "CRITICAL SKU 수", example = "1297")
        long criticalSkuCount,
        @Schema(description = "WARNING SKU 수", example = "2140")
        long warningSkuCount,
        @Schema(description = "CRITICAL과 WARNING을 합한 위험 SKU 수", example = "3437")
        long riskSkuCount,
        @Schema(description = "CRITICAL 재고수량", example = "182430")
        BigDecimal criticalStockQty,
        @Schema(description = "WARNING 재고수량", example = "759791")
        BigDecimal warningStockQty,
        @Schema(description = "CRITICAL과 WARNING을 합한 위험재고 수량", example = "942221")
        BigDecimal riskStockQty,
        @Schema(description = "전체 재고 중 위험재고 비율(%)", example = "24.8570")
        BigDecimal riskStockRatio,
        @Schema(description = "향후 30일 예상 폐기수량", example = "519")
        BigDecimal expectedDisposalQty30d,
        @Schema(description = "향후 30일 예상 폐기손실액", example = "3373500")
        BigDecimal expectedDisposalLossAmount30d,
        @Schema(description = "안전재고 미만 SKU 수", example = "6821")
        long shortageSkuCount
) {
}
