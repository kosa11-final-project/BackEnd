package com.stockit.backend.feature.statistics.dto.response;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "운영 유형 또는 개별 위치별 AI 전략 성과")
public record StrategyLocationPerformanceResponse(
        String id,
        String code,
        String name,
        String scopeType,
        long completedCount,
        BigDecimal goalAchievementRate,
        BigDecimal baselineRiskStockQty,
        BigDecimal riskStockReductionQty,
        BigDecimal riskStockReductionRate,
        BigDecimal estimatedLossSavingsAmount
) {
}
