package com.stockit.backend.feature.statistics.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "실행 종료일별 AI 전략 성과")
public record StrategyStatisticsTrendPointResponse(
        LocalDate date,
        long completedCount,
        long goalAchievedCount,
        BigDecimal achievementRate,
        BigDecimal baselineRiskStockQty,
        BigDecimal riskStockReductionQty,
        BigDecimal avoidedDisposalQty,
        BigDecimal estimatedLossSavingsAmount
) {
}
