package com.stockit.backend.feature.statistics.dto.response;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "위험등급별 SKU 및 재고수량")
public record RiskGradeStatisticsResponse(
        @Schema(description = "위험등급", example = "CRITICAL")
        String riskGrade,
        @Schema(description = "해당 대표등급의 SKU 수", example = "1297")
        long skuCount,
        @Schema(description = "해당 등급으로 평가된 재고수량", example = "182430")
        BigDecimal stockQty
) {
}
