package com.stockit.backend.feature.strategy.calculation.candidate.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.stockit.backend.feature.strategy.domain.StrategyType;

class StrategyCandidateIdGeneratorTest {

    private final StrategyCandidateIdGenerator generator =
            new StrategyCandidateIdGenerator();

    @Test
    void includesDiscountPriceAndRateInStableCandidateIdentity() {
        StrategyCandidate.Action fivePercent = discountAction("95", "0.0500");
        StrategyCandidate.Action sameRateDifferentPrice =
                discountAction("94", "0.0500");
        StrategyCandidate.Action samePriceDifferentRate =
                discountAction("95", "0.1000");

        String first = generate(fivePercent);

        assertThat(generate(fivePercent)).isEqualTo(first);
        assertThat(generate(sameRateDifferentPrice)).isNotEqualTo(first);
        assertThat(generate(samePriceDifferentRate)).isNotEqualTo(first);
    }

    @Test
    void normalizesScientificDecimalNotationInCandidateIdentity() {
        StrategyCandidate.Action plain = discountAction("95", "0.0500");
        StrategyCandidate.Action scientific = discountAction("9.5E+1", "5.00E-2");

        assertThat(generate(scientific)).isEqualTo(generate(plain));
    }

    private String generate(StrategyCandidate.Action action) {
        return generator.generate(
                StrategyType.PRICE_DISCOUNT,
                LocalDate.of(2026, 8, 20),
                LocalDate.of(2026, 8, 27),
                List.of(action)
        );
    }

    private static StrategyCandidate.Action discountAction(
            String price,
            String rate
    ) {
        StrategyCandidate.Location location = new StrategyCandidate.Location(501L, 10L);
        return new StrategyCandidate.Action(
                StrategyType.PRICE_DISCOUNT,
                location,
                location,
                decimal("10"),
                decimal("0"),
                decimal(price),
                decimal(rate),
                List.of(new StrategyCandidate.LotAllocation(
                        1L, 1001L, decimal("10"), 1
                ))
        );
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
