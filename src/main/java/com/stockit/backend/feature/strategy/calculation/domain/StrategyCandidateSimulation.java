package com.stockit.backend.feature.strategy.calculation.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.stockit.backend.feature.strategy.calculation.candidate.domain.CandidateAssumption;

/** 실행 가능한 후보 하나의 정량 평가 결과. */
public record StrategyCandidateSimulation(
        String candidateId,
        Summary summary,
        ComparisonToBaseline comparisonToBaseline,
        List<DailyPoint> dailySeries,
        List<CandidateAssumption> assumptions
) {
    public StrategyCandidateSimulation {
        if (candidateId == null || candidateId.isBlank()
                || summary == null || comparisonToBaseline == null
                || dailySeries == null || assumptions == null) {
            throw new IllegalArgumentException("candidate simulation is invalid");
        }
        dailySeries = List.copyOf(dailySeries);
        assumptions = List.copyOf(assumptions);
    }

    public record Summary(
            BigDecimal expectedSalesQty,
            BigDecimal expectedRevenue,
            BigDecimal totalContributionMargin,
            BigDecimal contributionMarginRate,
            Integer expectedSellThroughDays,
            BigDecimal expectedRemainingQty,
            BigDecimal expectedDisposalQty,
            BigDecimal estimatedActionCost,
            BigDecimal netEffect
    ) {
    }

    public record ComparisonToBaseline(
            BigDecimal salesQtyDelta,
            BigDecimal revenueDelta,
            BigDecimal contributionMarginDelta,
            BigDecimal remainingQtyReduction,
            BigDecimal disposalQtyReduction,
            BigDecimal netEffect
    ) {
    }

    public record DailyPoint(
            LocalDate date,
            BigDecimal expectedSalesQty,
            BigDecimal expectedRemainingQty,
            BigDecimal cumulativeRevenue,
            BigDecimal cumulativeContributionMargin
    ) {
    }
}
