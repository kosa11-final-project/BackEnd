package com.stockit.backend.feature.strategy.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.stockit.backend.feature.strategy.calculation.domain.StrategyCandidateSimulation;
import com.stockit.backend.feature.strategy.calculation.policy.SalesPointDiscountPolicy.SalesPointGroup;
import com.stockit.backend.feature.strategy.domain.StrategyType;

public record AdjustedAiStrategySimulationResponse(
        Long strategyCaseId,
        String candidateId,
        AdjustedConditions adjustedConditions,
        List<AdjustedAction> actions,
        AiStrategyPeriodConstraintsResponse adjustmentConstraints,
        AiStrategyChartRangeResponse chartRange,
        StrategyCandidateSimulation simulation
) {
    public AdjustedAiStrategySimulationResponse {
        actions = List.copyOf(actions);
    }

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

    public record AdjustedAction(
            int actionOrder,
            StrategyType actionType,
            BigDecimal actionQuantity,
            BigDecimal estimatedActionCost,
            MovementCost movementCost
    ) {
    }

    public record MovementCost(
            BigDecimal weightKg,
            BigDecimal distanceKm,
            BigDecimal costPerKgKm,
            BigDecimal estimatedCost
    ) {
    }
}
