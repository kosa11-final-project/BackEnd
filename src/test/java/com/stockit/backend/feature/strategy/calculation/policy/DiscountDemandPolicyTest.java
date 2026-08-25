package com.stockit.backend.feature.strategy.calculation.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.calculation.engine.CandidateSimulationException;

class DiscountDemandPolicyTest {

    private DiscountDemandPolicy demandPolicy;
    private SalesPointDiscountPolicy salesPointPolicy;

    @BeforeEach
    void setUp() {
        DiscountSimulationProperties properties = new DiscountSimulationProperties();
        salesPointPolicy = new SalesPointDiscountPolicy(properties);
        demandPolicy = new DiscountDemandPolicy(properties, salesPointPolicy);
    }

    @Test
    void appliesConstantElasticityAndCapsDemandMultiplier() {
        StrategyCalculationContext.SalesPoint general = salesPoint(
                "HMART_ASAN_HOSPITAL"
        );

        assertThat(demandPolicy.apply(decimal("10"), general, decimal("0.20")))
                .isEqualByComparingTo("12.500");
        assertThat(demandPolicy.apply(decimal("10"), general, decimal("0.30")))
                .isEqualByComparingTo("14.285");

        DiscountSimulationProperties cappedProperties =
                new DiscountSimulationProperties();
        cappedProperties.setPriceElasticity(decimal("3.0"));
        DiscountDemandPolicy capped = new DiscountDemandPolicy(
                cappedProperties,
                new SalesPointDiscountPolicy(cappedProperties)
        );
        assertThat(capped.apply(decimal("10"), general, decimal("0.30")))
                .isEqualByComparingTo("15.000");
    }

    @Test
    void usesDepartmentGeneralAndConservativeUnknownLimits() {
        assertThat(salesPointPolicy.resolve(salesPoint("DEPT_PANGYO")))
                .extracting(
                        SalesPointDiscountPolicy.DiscountPolicy::group,
                        SalesPointDiscountPolicy.DiscountPolicy::maximumDiscountRate
                )
                .containsExactly(
                        SalesPointDiscountPolicy.SalesPointGroup.DEPARTMENT_STORE,
                        decimal("0.20")
                );
        assertThat(salesPointPolicy.resolve(salesPoint("GREETING")).maximumDiscountRate())
                .isEqualByComparingTo("0.30");
        assertThat(salesPointPolicy.resolve(salesPoint("NEW_POINT")).maximumDiscountRate())
                .isEqualByComparingTo("0.20");
    }

    @Test
    void rejectsRateAboveSalesPointLimit() {
        assertThatThrownBy(() -> demandPolicy.apply(
                decimal("10"),
                salesPoint("DEPT_PANGYO"),
                decimal("0.25")
        )).isInstanceOfSatisfying(
                CandidateSimulationException.class,
                exception -> assertThat(exception.getCode())
                        .isEqualTo("CANDIDATE_DISCOUNT_RATE_EXCEEDED")
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
                Map.of(),
                List.of()
        );
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
