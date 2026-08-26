package com.stockit.backend.feature.strategy.calculation.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 무전략 상태의 요약 지표와 화면 차트용 일별 시계열. */
public record BaselineSimulation(
        Summary summary,
        List<DailyPoint> dailySeries
) {

    public BaselineSimulation {
        if (summary == null) {
            throw new IllegalArgumentException("simulation summary must not be null");
        }
        dailySeries = List.copyOf(dailySeries);
    }

    public record Summary(
            BigDecimal expectedSalesQty,
            BigDecimal expectedRevenue,
            BigDecimal totalContributionMargin,
            BigDecimal contributionMarginRate,
            Integer expectedSellThroughDays,
            BigDecimal expectedRemainingQty,
            BigDecimal expectedDisposalQty,
            BigDecimal expectedDisposalCost,
            BigDecimal expectedHoldingCost
    ) {
        public Summary(
                BigDecimal expectedSalesQty,
                BigDecimal expectedRevenue,
                BigDecimal totalContributionMargin,
                BigDecimal contributionMarginRate,
                Integer expectedSellThroughDays,
                BigDecimal expectedRemainingQty,
                BigDecimal expectedDisposalQty
        ) {
            this(
                    expectedSalesQty,
                    expectedRevenue,
                    totalContributionMargin,
                    contributionMarginRate,
                    expectedSellThroughDays,
                    expectedRemainingQty,
                    expectedDisposalQty,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO
            );
        }
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
