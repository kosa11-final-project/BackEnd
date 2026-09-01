package com.stockit.backend.feature.strategy.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.strategy.calculation.candidate.policy.StrategyPeriodEligibilityPolicy;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.domain.StrategyType;
import com.stockit.backend.feature.strategy.result.StrategyGenerationResult;
import com.stockit.backend.feature.strategy.result.StrategyResultStore;
import com.stockit.backend.feature.strategy.service.StrategyCaseLifecycleGuard;
import com.stockit.backend.feature.strategy.service.StrategyDateTimeProvider;
import com.stockit.backend.feature.strategy.simulation.StrategyAdjustmentSimulationService;
import com.stockit.backend.feature.strategy.simulation.StrategySimulationContextStore;

class StrategySelectionResolverTest {

    @Test
    void reportsPastStartAndShortenedSellableEndTogether() {
        StrategyResultStore resultStore = mock(StrategyResultStore.class);
        StrategySimulationContextStore contextStore = mock(
                StrategySimulationContextStore.class
        );
        StrategyAdjustmentSimulationService adjustmentService = mock(
                StrategyAdjustmentSimulationService.class
        );
        StrategyPeriodEligibilityPolicy periodPolicy = mock(
                StrategyPeriodEligibilityPolicy.class
        );
        StrategyCaseLifecycleGuard lifecycleGuard = mock(
                StrategyCaseLifecycleGuard.class
        );
        StrategyDateTimeProvider dateTimeProvider = mock(
                StrategyDateTimeProvider.class
        );
        StrategyGenerationResult result = mock(StrategyGenerationResult.class);
        StrategyCalculationContext context = mock(StrategyCalculationContext.class);
        StrategyGenerationResult.Option option = mock(
                StrategyGenerationResult.Option.class
        );
        StrategyGenerationResult.Candidate candidate = candidate();
        LocalDate businessDate = LocalDate.of(2026, 8, 25);
        LocalDate latestEnd = LocalDate.of(2026, 8, 31);
        LocalDateTime validatedAt = LocalDateTime.of(2026, 8, 25, 10, 30);

        when(resultStore.find(123L)).thenReturn(Optional.of(result));
        when(contextStore.find(123L)).thenReturn(Optional.of(context));
        when(result.options()).thenReturn(List.of(option));
        when(option.candidate()).thenReturn(candidate);
        when(periodPolicy.minimumStartDate(context, businessDate))
                .thenReturn(businessDate);
        when(periodPolicy.latestSelectableEndDate(context, List.of(1L)))
                .thenReturn(latestEnd);
        when(dateTimeProvider.now()).thenReturn(validatedAt);

        StrategySelectionResolver resolver = new StrategySelectionResolver(
                resultStore,
                contextStore,
                adjustmentService,
                periodPolicy,
                lifecycleGuard,
                dateTimeProvider,
                mock(StrategyAppliedQuantityCalculator.class),
                mock(StrategySelectionFingerprintFactory.class)
        );

        assertThatThrownBy(() -> resolver.resolve(123L, "CAND-1", null))
                .isInstanceOfSatisfying(AppException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(
                            ErrorCode.AI_STRATEGY_EXECUTION_CONDITION_CHANGED
                    );
                    var details = (StrategyExecutionConditionChangedDetails)
                            exception.getDetails();
                    assertThat(details.validatedAt()).isEqualTo(validatedAt);
                    assertThat(details.changes())
                            .extracting(StrategyExecutionConditionChange::type)
                            .containsExactly(
                                    StrategyExecutionConditionChangeType
                                            .START_DATE_PASSED,
                                    StrategyExecutionConditionChangeType
                                            .SELLABLE_END_DATE_CHANGED
                            );
                    assertThat(details.changes().get(0).suggestedValue())
                            .isEqualTo(businessDate);
                    assertThat(details.changes().get(1).suggestedValue())
                            .isEqualTo(latestEnd);
                });
    }

    private static StrategyGenerationResult.Candidate candidate() {
        return new StrategyGenerationResult.Candidate(
                "CAND-1",
                List.of(StrategyType.PRICE_DISCOUNT),
                LocalDate.of(2026, 8, 24),
                LocalDate.of(2026, 9, 5),
                List.of(new StrategyGenerationResult.Action(
                        StrategyType.PRICE_DISCOUNT,
                        501L,
                        10L,
                        501L,
                        10L,
                        BigDecimal.TEN,
                        BigDecimal.ZERO,
                        new BigDecimal("8500"),
                        new BigDecimal("0.15"),
                        List.of(new StrategyGenerationResult.LotAllocation(
                                1L, 1001L, BigDecimal.TEN, 1
                        ))
                )),
                List.of(),
                new StrategyGenerationResult.Preference(1, 1, 100),
                BigDecimal.TEN
        );
    }
}
