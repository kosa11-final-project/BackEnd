package com.stockit.backend.feature.statistics.dto.response;

import java.math.BigDecimal;
import java.util.List;

import com.stockit.backend.feature.statistics.vo.InventoryStatisticsAggregateVO;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "선택 범위의 재고 통계 요약")
public record InventoryStatisticsSummaryResponse(
        @Schema(description = "현재 재고가 존재하는 고유 SKU 수", example = "9277")
        long totalSkuCount,
        @Schema(description = "판매처 미할당 재고를 포함한 전체 재고수량", example = "3208895")
        BigDecimal totalStockQty,
        @Schema(description = "판매 가능한 가용 재고수량", example = "3070127")
        BigDecimal availableStockQty,
        @Schema(description = "대표 위험등급이 CRITICAL인 SKU 수", example = "1297")
        long criticalSkuCount,
        @Schema(description = "CRITICAL 평가를 받은 재고수량", example = "182430")
        BigDecimal criticalStockQty,
        @Schema(description = "안전재고 미만인 SKU 수", example = "6821")
        long shortageSkuCount,
        @Schema(description = "향후 30일 예상 폐기수량", example = "519")
        BigDecimal expectedDisposalQty30d,
        @Schema(description = "위험등급별 분포")
        List<RiskGradeStatisticsResponse> riskDistribution,
        @Schema(description = "집계 데이터 품질")
        InventoryStatisticsDataQualityResponse dataQuality,
        @Schema(description = "원가 기준 금액 지표")
        InventoryFinancialStatisticsResponse financialSummary
) {

    public static InventoryStatisticsSummaryResponse from(InventoryStatisticsAggregateVO value) {
        List<RiskGradeStatisticsResponse> distribution = List.of(
                new RiskGradeStatisticsResponse("CRITICAL", value.getCriticalSkuCount(), value.getCriticalStockQty()),
                new RiskGradeStatisticsResponse("WARNING", value.getWarningSkuCount(), value.getWarningStockQty()),
                new RiskGradeStatisticsResponse("NORMAL", value.getNormalSkuCount(), value.getNormalStockQty()),
                new RiskGradeStatisticsResponse("GOOD", value.getGoodSkuCount(), value.getGoodStockQty()),
                new RiskGradeStatisticsResponse("UNASSESSED", value.getUnassessedDistributionSkuCount(),
                        value.getUnassessedDistributionStockQty())
        );
        InventoryStatisticsDataQualityResponse quality = new InventoryStatisticsDataQualityResponse(
                value.getUnassessedSkuCount(),
                value.getUnassessedStockQty(),
                value.getMissingForecastSkuCount(),
                value.getMissingForecastStockQty()
        );
        InventoryFinancialStatisticsResponse financial = new InventoryFinancialStatisticsResponse(
                value.getTotalInventoryCostAmount(),
                value.getCriticalInventoryCostAmount(),
                value.getExpectedDisposalLossAmount30d(),
                value.getMissingCostSkuCount(),
                value.getMissingCostStockQty()
        );
        return new InventoryStatisticsSummaryResponse(
                value.getTotalSkuCount(),
                value.getTotalStockQty(),
                value.getAvailableStockQty(),
                value.getCriticalSkuCount(),
                value.getCriticalStockQty(),
                value.getShortageSkuCount(),
                value.getExpectedDisposalQty30d(),
                distribution,
                quality,
                financial
        );
    }
}
