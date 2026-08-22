package com.stockit.backend.feature.statistics.dto.response;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "실제 실행 액션 조합별 AI 전략 성과")
public record StrategyActionCombinationStatisticsResponse(
        String code,
        String label,
        long completedCount,
        BigDecimal averageAchievementRate,
        BigDecimal riskReductionRate,
        BigDecimal avoidedDisposalQty,
        BigDecimal estimatedLossSavingsAmount
) {
}
