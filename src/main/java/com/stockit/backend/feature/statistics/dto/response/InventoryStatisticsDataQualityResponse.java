package com.stockit.backend.feature.statistics.dto.response;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "통계 집계에서 누락되거나 제외된 데이터 현황")
public record InventoryStatisticsDataQualityResponse(
        @Schema(description = "최신 위험평가가 없는 SKU 수", example = "56")
        long unassessedSkuCount,
        @Schema(description = "최신 위험평가가 없는 재고수량", example = "48133")
        BigDecimal unassessedStockQty,
        @Schema(description = "30일 예상 폐기 계산에 필요한 수요예측이 없는 SKU 수", example = "83")
        long missingForecastSkuCount,
        @Schema(description = "수요예측 누락으로 예상 폐기 계산에서 제외된 재고수량", example = "17240")
        BigDecimal missingForecastStockQty
) {
}
