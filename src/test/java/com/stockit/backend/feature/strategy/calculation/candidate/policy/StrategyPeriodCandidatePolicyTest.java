package com.stockit.backend.feature.strategy.calculation.candidate.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.stockit.backend.feature.strategy.calculation.candidate.domain.StrategyPeriodCandidate;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;

class StrategyPeriodCandidatePolicyTest {

    private final StrategyPeriodCandidatePolicy policy =
            new StrategyPeriodCandidatePolicy(
                    new StrategyPeriodEligibilityPolicy()
            );

    @Test
    void startOnlyUsesRemainingRequestDateForecastHorizonInsteadOfNewNinetyDays() {
        LocalDate start = LocalDate.of(2026, 9, 15);
        LocalDate requestDateMaxEnd = LocalDate.of(2026, 10, 29);
        StrategyCalculationContext context = mock(StrategyCalculationContext.class);
        when(context.forecastStartDate()).thenReturn(start);
        when(context.forecastEndDate()).thenReturn(requestDateMaxEnd);
        when(context.evaluationInventory()).thenReturn(List.of(
                new StrategyCalculationContext.InventoryLot(
                        1L, 1001L, 501L, 10L, 10L,
                        BigDecimal.TEN, BigDecimal.ZERO, null,
                        start.minusDays(10), null, null, "AVAILABLE"
                )
        ));
        when(context.requestConstraints()).thenReturn(
                new StrategyCalculationContext.RequestConstraints(
                        List.of(),
                        List.of(),
                        start,
                        null
                )
        );

        List<StrategyPeriodCandidate> result = policy.generate(context, 10L);

        assertThat(result).containsExactly(
                new StrategyPeriodCandidate(start, start.plusDays(6)),
                new StrategyPeriodCandidate(start, start.plusDays(13)),
                new StrategyPeriodCandidate(start, start.plusDays(29)),
                new StrategyPeriodCandidate(start, requestDateMaxEnd)
        );
        assertThat(result).allSatisfy(period ->
                assertThat(period.endDate()).isBeforeOrEqualTo(requestDateMaxEnd));
    }
}
