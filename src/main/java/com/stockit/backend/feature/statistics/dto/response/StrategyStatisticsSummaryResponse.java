package com.stockit.backend.feature.statistics.dto.response;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "종료된 AI 전략의 핵심 성과 합계")
public record StrategyStatisticsSummaryResponse(
        long completedCount,
        long goalAchievedCount,
        BigDecimal goalAchievedStrategyRate,
        BigDecimal averageAchievementRate,
        BigDecimal baselineRiskStockQty,
        BigDecimal riskStockReductionQty,
        BigDecimal riskStockReductionRate,
        BigDecimal avoidedDisposalQty,
        BigDecimal estimatedLossSavingsAmount
) {
}
