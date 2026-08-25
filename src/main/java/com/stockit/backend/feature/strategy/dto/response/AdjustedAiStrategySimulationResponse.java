package com.stockit.backend.feature.strategy.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.stockit.backend.feature.strategy.calculation.domain.StrategyCandidateSimulation;
import com.stockit.backend.feature.strategy.calculation.policy.SalesPointDiscountPolicy.SalesPointGroup;

public record AdjustedAiStrategySimulationResponse(
        Long strategyCaseId,
        String candidateId,
        AdjustedConditions adjustedConditions,
        AiStrategyPeriodConstraintsResponse adjustmentConstraints,
        AiStrategyChartRangeResponse chartRange,
        StrategyCandidateSimulation simulation
) {
    public record AdjustedConditions(
            BigDecimal actionQuantity,
            BigDecimal discountRate,
            BigDecimal strategyPrice,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal maximumExecutableQuantity,
            SalesPointGroup salesPointGroup,
            BigDecimal maximumDiscountRate
    ) {
    }
}
