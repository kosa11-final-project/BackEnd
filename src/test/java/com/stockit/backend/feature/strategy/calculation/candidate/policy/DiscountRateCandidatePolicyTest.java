package com.stockit.backend.feature.strategy.calculation.candidate.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.calculation.policy.DiscountSimulationProperties;
import com.stockit.backend.feature.strategy.calculation.policy.SalesPointDiscountPolicy;

class DiscountRateCandidatePolicyTest {

    private final DiscountSimulationProperties properties =
            new DiscountSimulationProperties();
    private final DiscountRateCandidatePolicy policy =
            new DiscountRateCandidatePolicy(new SalesPointDiscountPolicy(properties));

    @Test
    void createsFivePercentStepsWithoutCrossingMinimumSellingPrice() {
        StrategyCalculationContext.Price price = new StrategyCalculationContext.Price(
                1L,
                decimal("12000"),
                decimal("10000"),
                decimal("7300"),
                decimal("300"),
                decimal("500")
        );

        assertThat(policy.generate(price, salesPoint("HMART_ASAN_HOSPITAL")))
                .extracting(DiscountRateCandidatePolicy.DiscountOption::discountRate)
                .containsExactly(
                        decimal("0.0500"),
                        decimal("0.1000"),
                        decimal("0.1500"),
                        decimal("0.2000"),
                        decimal("0.2500")
                );
    }

    @Test
    void returnsEmptyWhenMinimumPriceIsMissingOrFivePercentIsNotAllowed() {
        StrategyCalculationContext.Price missingMinimum =
                new StrategyCalculationContext.Price(
                        1L, decimal("120"), decimal("100"), null,
                        decimal("5"), decimal("10")
                );
        StrategyCalculationContext.Price narrowMargin =
                new StrategyCalculationContext.Price(
                        2L, decimal("120"), decimal("100"), decimal("98"),
                        decimal("5"), decimal("10")
                );

        assertThat(policy.generate(
                missingMinimum,
                salesPoint("HMART_ASAN_HOSPITAL")
        )).isEmpty();
        assertThat(policy.generate(
                narrowMargin,
                salesPoint("HMART_ASAN_HOSPITAL")
        )).isEmpty();
    }

    @Test
    void capsDepartmentStoreDiscountAtTwentyPercent() {
        StrategyCalculationContext.Price price = new StrategyCalculationContext.Price(
                1L,
                decimal("12000"),
                decimal("10000"),
                decimal("5000"),
                decimal("300"),
                decimal("500")
        );

        assertThat(policy.generate(price, salesPoint("DEPT_PANGYO")))
                .extracting(DiscountRateCandidatePolicy.DiscountOption::discountRate)
                .containsExactly(
                        decimal("0.0500"),
                        decimal("0.1000"),
                        decimal("0.1500"),
                        decimal("0.2000")
                );
    }

    private static StrategyCalculationContext.SalesPoint salesPoint(String code) {
        return new StrategyCalculationContext.SalesPoint(
                10L,
                code,
                code,
                BigDecimal.ZERO,
                false,
                null,
                java.util.Map.of(),
                java.util.List.of()
        );
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
