package com.stockit.backend.feature.statistics.dto.response;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "개별 물류센터 또는 판매처의 재고 통계")
public record InventoryStatisticsLocationResponse(
        String id,
        String name,
        String code,
        String scopeType,
        String region,
        long totalSkuCount,
        BigDecimal totalStockQty,
        BigDecimal availableStockQty,
        long criticalSkuCount,
        BigDecimal criticalStockQty,
        long shortageSkuCount,
        BigDecimal expectedDisposalQty30d,
        List<RiskGradeStatisticsResponse> riskDistribution,
        InventoryStatisticsDataQualityResponse dataQuality,
        InventoryFinancialStatisticsResponse financialSummary,
        @Schema(description = "전체 재고 중 CRITICAL 재고 비율", example = "5.69")
        BigDecimal criticalStockRatio
) {

    public static InventoryStatisticsLocationResponse from(
            String scopeType,
            String scopeCode,
            String scopeName,
            String region,
            InventoryStatisticsSummaryResponse summary
    ) {
        BigDecimal ratio = BigDecimal.ZERO;
        if (summary.totalStockQty().signum() > 0) {
            ratio = summary.criticalStockQty()
                    .multiply(BigDecimal.valueOf(100))
                    .divide(summary.totalStockQty(), 4, RoundingMode.HALF_UP);
        }
        return new InventoryStatisticsLocationResponse(
                scopeCode,
                scopeName,
                scopeCode,
                scopeType,
                region,
                summary.totalSkuCount(),
                summary.totalStockQty(),
                summary.availableStockQty(),
                summary.criticalSkuCount(),
                summary.criticalStockQty(),
                summary.shortageSkuCount(),
                summary.expectedDisposalQty30d(),
                summary.riskDistribution(),
                summary.dataQuality(),
                summary.financialSummary(),
                ratio
        );
    }
}
