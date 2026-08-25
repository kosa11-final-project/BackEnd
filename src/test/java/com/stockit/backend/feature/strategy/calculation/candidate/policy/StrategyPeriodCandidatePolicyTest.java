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
import com.stockit.backend.feature.strategy.service.StrategyDateTimeProvider;

class StrategyPeriodCandidatePolicyTest {

    private final StrategyDateTimeProvider dateTimeProvider = mock(
            StrategyDateTimeProvider.class
    );
    private final StrategyPeriodCandidatePolicy policy =
            new StrategyPeriodCandidatePolicy(
                    new StrategyPeriodEligibilityPolicy(),
                    dateTimeProvider
            );

    @Test
    void startOnlyUsesRemainingRequestDateForecastHorizonInsteadOfNewNinetyDays() {
        LocalDate start = LocalDate.of(2026, 9, 15);
        LocalDate requestDateMaxEnd = LocalDate.of(2026, 10, 29);
        when(dateTimeProvider.now()).thenReturn(start.atStartOfDay());
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

    @Test
    void rejectsFixedStartThatBecamePastBeforeCandidateGenerationRetry() {
        LocalDate forecastStart = LocalDate.of(2026, 8, 20);
        LocalDate forecastEnd = LocalDate.of(2026, 8, 27);
        StrategyCalculationContext context = mock(StrategyCalculationContext.class);
        when(dateTimeProvider.now()).thenReturn(
                LocalDate.of(2026, 8, 21).atStartOfDay()
        );
        when(context.forecastStartDate()).thenReturn(forecastStart);
        when(context.forecastEndDate()).thenReturn(forecastEnd);
        when(context.evaluationInventory()).thenReturn(List.of(
                new StrategyCalculationContext.InventoryLot(
                        1L, 1001L, 501L, 10L, 10L,
                        BigDecimal.TEN, BigDecimal.ZERO, null,
                        forecastStart.minusDays(10), null, null, "AVAILABLE"
                )
        ));
        when(context.requestConstraints()).thenReturn(
                new StrategyCalculationContext.RequestConstraints(
                        List.of(), List.of(), forecastStart, null
                )
        );

        assertThat(policy.generate(context, 10L)).isEmpty();
    }
}
