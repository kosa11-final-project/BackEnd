package com.stockit.backend.feature.strategy.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.strategy.calculation.candidate.domain.CandidateExclusion;
import com.stockit.backend.feature.strategy.calculation.candidate.domain.CandidateExclusionReason;
import com.stockit.backend.feature.strategy.calculation.candidate.domain.CandidateGenerationResult;
import com.stockit.backend.feature.strategy.calculation.candidate.domain.StrategyCandidate;
import com.stockit.backend.feature.strategy.calculation.candidate.policy.StrategyPeriodEligibilityPolicy;
import com.stockit.backend.feature.strategy.calculation.candidate.service.StrategyCandidateGenerationService;
import com.stockit.backend.feature.strategy.calculation.domain.BaselineSimulation;
import com.stockit.backend.feature.strategy.calculation.domain.SimulationDetailLevel;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCandidateSimulation;
import com.stockit.backend.feature.strategy.calculation.engine.StrategyCandidateSimulationEngine;
import com.stockit.backend.feature.strategy.calculation.policy.DiscountSimulationProperties;
import com.stockit.backend.feature.strategy.calculation.policy.SalesPointDiscountPolicy;
import com.stockit.backend.feature.strategy.domain.StrategyType;
import com.stockit.backend.feature.strategy.result.StrategyGenerationResult;
import com.stockit.backend.feature.strategy.result.StrategyResultStore;
import com.stockit.backend.feature.strategy.service.StrategyCaseLifecycleGuard;
import com.stockit.backend.feature.strategy.service.StrategyDateTimeProvider;

@ExtendWith(MockitoExtension.class)
class StrategyAdjustmentSimulationServiceImplTest {

    private static final LocalDate START = LocalDate.of(2026, 8, 20);
    private static final LocalDate END = LocalDate.of(2026, 8, 27);

    @Mock private StrategyResultStore resultStore;
    @Mock private StrategySimulationContextStore contextStore;
    @Mock private StrategyCandidateGenerationService generationService;
    @Mock private StrategyCandidateSimulationEngine simulationEngine;
    @Mock private StrategyCaseLifecycleGuard lifecycleGuard;
    @Mock private StrategyDateTimeProvider dateTimeProvider;

    private StrategyAdjustmentSimulationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new StrategyAdjustmentSimulationServiceImpl(
                resultStore,
                contextStore,
                generationService,
                simulationEngine,
                new SalesPointDiscountPolicy(new DiscountSimulationProperties()),
                new StrategyPeriodEligibilityPolicy(),
                lifecycleGuard,
                dateTimeProvider
        );
    }

    @Test
    void recalculatesAdjustedQuantityFromStoredSnapshotWithoutMutatingResult() {
        StrategyCalculationContext context = context();
        StrategyGenerationResult result = mock(StrategyGenerationResult.class);
        StrategyGenerationResult.Option option = mock(
                StrategyGenerationResult.Option.class
        );
        StrategyGenerationResult.Candidate template = template();
        BaselineSimulation baseline = mock(BaselineSimulation.class);
        StrategyCandidateSimulation simulation = mock(
                StrategyCandidateSimulation.class
        );

        when(resultStore.find(1L)).thenReturn(Optional.of(result));
        when(contextStore.find(1L)).thenReturn(Optional.of(context));
        when(dateTimeProvider.now()).thenReturn(
                LocalDateTime.of(2026, 8, 20, 10, 0)
        );
        when(result.options()).thenReturn(List.of(option));
        when(result.baselineSimulation()).thenReturn(baseline);
        when(option.candidate()).thenReturn(template);
        when(generationService.generate(any())).thenReturn(
                new CandidateGenerationResult(List.of(baseCandidate()), List.of())
        );
        when(simulationEngine.simulate(
                any(), any(), eq(baseline), eq(SimulationDetailLevel.WITH_DAILY_SERIES)
        )).thenReturn(simulation);

        var response = service.simulate(
                1L,
                "CAND-1",
                new AdjustStrategySimulationCommand(
                        decimal("6"), decimal("0.15"), START, END
                )
        );

        ArgumentCaptor<StrategyCandidate> candidateCaptor =
                ArgumentCaptor.forClass(StrategyCandidate.class);
        verify(simulationEngine).simulate(
                any(), candidateCaptor.capture(), eq(baseline),
                eq(SimulationDetailLevel.WITH_DAILY_SERIES)
        );
        StrategyCandidate adjusted = candidateCaptor.getValue();
        assertThat(adjusted.actions().get(0).actionQuantity())
                .isEqualByComparingTo("6");
        assertThat(adjusted.actions().get(0).lotAllocations().get(0).quantity())
                .isEqualByComparingTo("6");
        assertThat(response.adjustedConditions().strategyPrice())
                .isEqualByComparingTo("85");
        assertThat(response.adjustedConditions().salesPointGroup())
                .isEqualTo(SalesPointDiscountPolicy.SalesPointGroup.DEPARTMENT_STORE);
        assertThat(response.adjustedConditions().maximumDiscountRate())
                .isEqualByComparingTo("0.20");
        assertThat(response.adjustmentConstraints().requiresPeriodAdjustment())
                .isFalse();
        assertThat(response.chartRange().startDate()).isEqualTo(START);
        verify(lifecycleGuard).requireAdjustable(1L);
    }

    @Test
    void rejectsStartDateThatHasBecomePastSinceGeneration() {
        StrategyGenerationResult result = mock(StrategyGenerationResult.class);
        StrategyGenerationResult.Option option = mock(
                StrategyGenerationResult.Option.class
        );
        when(resultStore.find(1L)).thenReturn(Optional.of(result));
        when(contextStore.find(1L)).thenReturn(Optional.of(context()));
        when(result.options()).thenReturn(List.of(option));
        when(option.candidate()).thenReturn(template());
        when(dateTimeProvider.now()).thenReturn(
                LocalDateTime.of(2026, 8, 21, 10, 0)
        );

        assertThatThrownBy(() -> service.simulate(
                1L,
                "CAND-1",
                new AdjustStrategySimulationCommand(
                        decimal("6"), decimal("0.15"), START, END
                )
        )).isInstanceOfSatisfying(
                AppException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.AI_STRATEGY_PERIOD_STALE)
        );
    }

    @Test
    void revalidatesEndDateAgainstLotsRemainingAfterQuantityResize() {
        LocalDate adjustedEnd = LocalDate.of(2026, 8, 26);
        StrategyCalculationContext.InventoryLot first = lot(
                1L, 1001L, decimal("5"), LocalDate.of(2026, 8, 22)
        );
        StrategyCalculationContext.InventoryLot second = lot(
                2L, 1002L, decimal("5"), LocalDate.of(2026, 8, 27)
        );
        StrategyCalculationContext context = context(List.of(first, second));
        StrategyGenerationResult result = mock(StrategyGenerationResult.class);
        StrategyGenerationResult.Option option = mock(
                StrategyGenerationResult.Option.class
        );
        when(resultStore.find(1L)).thenReturn(Optional.of(result));
        when(contextStore.find(1L)).thenReturn(Optional.of(context));
        when(result.options()).thenReturn(List.of(option));
        when(option.candidate()).thenReturn(template());
        when(dateTimeProvider.now()).thenReturn(
                LocalDateTime.of(2026, 8, 20, 10, 0)
        );
        when(generationService.generate(any())).thenReturn(
                new CandidateGenerationResult(
                        List.of(baseCandidateWithTwoLots(adjustedEnd)),
                        List.of()
                )
        );

        assertThatThrownBy(() -> service.simulate(
                1L,
                "CAND-1",
                new AdjustStrategySimulationCommand(
                        decimal("5"), decimal("0.15"), START, adjustedEnd
                )
        )).isInstanceOfSatisfying(
                AppException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(
                        ErrorCode.AI_STRATEGY_SELLABLE_END_EXCEEDED
                )
        );
    }

    @Test
    void reportsExpiredWhenCalculationSnapshotIsGone() {
        StrategyGenerationResult result = mock(StrategyGenerationResult.class);
        when(resultStore.find(1L)).thenReturn(Optional.of(result));
        when(contextStore.find(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.simulate(
                1L,
                "CAND-1",
                new AdjustStrategySimulationCommand(
                        decimal("1"), decimal("0.05"), START, END
                )
        )).isInstanceOfSatisfying(
                AppException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.AI_STRATEGY_RESULT_EXPIRED)
        );
    }

    @Test
    void reportsSellableEndExceededForMatchingSelectionExclusion() {
        stubSelection(new CandidateGenerationResult(
                List.of(),
                List.of(new CandidateExclusion(
                        StrategyType.PRICE_DISCOUNT,
                        10L,
                        CandidateExclusionReason.LOT_NOT_SELLABLE_IN_PERIOD,
                        "LOT 판매 가능 기간을 초과했습니다."
                ))
        ));

        assertThatThrownBy(() -> service.simulate(
                1L,
                "CAND-1",
                new AdjustStrategySimulationCommand(
                        decimal("6"), decimal("0.15"), START, END
                )
        )).isInstanceOfSatisfying(
                AppException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.AI_STRATEGY_SELLABLE_END_EXCEEDED)
        );
    }

    @Test
    void doesNotMisclassifyQuantityMismatchAsSellableEndExceeded() {
        stubSelection(new CandidateGenerationResult(
                List.of(baseCandidate()),
                List.of(new CandidateExclusion(
                        StrategyType.PRICE_DISCOUNT,
                        10L,
                        CandidateExclusionReason.LOT_NOT_SELLABLE_IN_PERIOD,
                        "다른 수량 변형의 LOT 판매 가능 기간을 초과했습니다."
                ))
        ));

        assertThatThrownBy(() -> service.simulate(
                1L,
                "CAND-1",
                new AdjustStrategySimulationCommand(
                        decimal("11"), decimal("0.15"), START, END
                )
        )).isInstanceOfSatisfying(
                AppException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.AI_STRATEGY_SIMULATION_INVALID)
        );
    }

    private void stubSelection(CandidateGenerationResult generated) {
        StrategyGenerationResult result = mock(StrategyGenerationResult.class);
        StrategyGenerationResult.Option option = mock(
                StrategyGenerationResult.Option.class
        );
        when(resultStore.find(1L)).thenReturn(Optional.of(result));
        when(contextStore.find(1L)).thenReturn(Optional.of(context()));
        when(result.options()).thenReturn(List.of(option));
        when(option.candidate()).thenReturn(template());
        when(dateTimeProvider.now()).thenReturn(
                LocalDateTime.of(2026, 8, 20, 10, 0)
        );
        when(generationService.generate(any())).thenReturn(generated);
    }

    private static StrategyGenerationResult.Candidate template() {
        return new StrategyGenerationResult.Candidate(
                "CAND-1",
                List.of(StrategyType.PRICE_DISCOUNT),
                START,
                END,
                List.of(new StrategyGenerationResult.Action(
                        StrategyType.PRICE_DISCOUNT,
                        501L,
                        10L,
                        501L,
                        10L,
                        decimal("10"),
                        BigDecimal.ZERO,
                        decimal("90"),
                        decimal("0.10"),
                        List.of(new StrategyGenerationResult.LotAllocation(
                                1L, 1001L, decimal("10"), 1
                        ))
                )),
                List.of(),
                new StrategyGenerationResult.Preference(1, 1, 100),
                decimal("10")
        );
    }

    private static StrategyCandidate baseCandidate() {
        StrategyCandidate.Location location = new StrategyCandidate.Location(
                501L, 10L
        );
        return new StrategyCandidate(
                "BASE",
                List.of(StrategyType.PRICE_DISCOUNT),
                START,
                END,
                List.of(new StrategyCandidate.Action(
                        StrategyType.PRICE_DISCOUNT,
                        location,
                        location,
                        decimal("10"),
                        BigDecimal.ZERO,
                        decimal("85"),
                        decimal("0.15"),
                        List.of(new StrategyCandidate.LotAllocation(
                                1L, 1001L, decimal("10"), 1
                        ))
                )),
                List.of(),
                new StrategyCandidate.Preference(1, 1, 100),
                new StrategyCandidate.DiscountEvidence(
                        decimal("10"), decimal("10"), decimal("20"),
                        decimal("10"), decimal("100"), decimal("70")
                )
        );
    }

    private static StrategyCandidate baseCandidateWithTwoLots(LocalDate endDate) {
        StrategyCandidate.Location location = new StrategyCandidate.Location(
                501L, 10L
        );
        return new StrategyCandidate(
                "BASE-TWO-LOTS",
                List.of(StrategyType.PRICE_DISCOUNT),
                START,
                endDate,
                List.of(new StrategyCandidate.Action(
                        StrategyType.PRICE_DISCOUNT,
                        location,
                        location,
                        decimal("10"),
                        BigDecimal.ZERO,
                        decimal("85"),
                        decimal("0.15"),
                        List.of(
                                new StrategyCandidate.LotAllocation(
                                        1L, 1001L, decimal("5"), 1
                                ),
                                new StrategyCandidate.LotAllocation(
                                        2L, 1002L, decimal("5"), 2
                                )
                        )
                )),
                List.of(),
                new StrategyCandidate.Preference(1, 1, 100),
                new StrategyCandidate.DiscountEvidence(
                        decimal("10"), decimal("10"), decimal("20"),
                        decimal("10"), decimal("100"), decimal("70")
                )
        );
    }

    private static StrategyCalculationContext context() {
        return context(List.of(lot(1L, 1001L, decimal("10"), null)));
    }

    private static StrategyCalculationContext context(
            List<StrategyCalculationContext.InventoryLot> lots
    ) {
        Map<LocalDate, BigDecimal> forecasts = new LinkedHashMap<>();
        for (LocalDate date = START; !date.isAfter(END); date = date.plusDays(1)) {
            forecasts.put(date, decimal("2"));
        }
        StrategyCalculationContext.Price price =
                new StrategyCalculationContext.Price(
                        1L, decimal("120"), decimal("100"), decimal("70"),
                        decimal("5"), decimal("10")
                );
        StrategyCalculationContext.SalesPoint salesPoint =
                new StrategyCalculationContext.SalesPoint(
                        10L, "DEPT_PANGYO", "판교점", BigDecimal.ZERO,
                        true, price, forecasts, List.of()
                );
        return new StrategyCalculationContext(
                1L, 10L, LocalDateTime.of(2026, 8, 20, 9, 0),
                START, END,
                new StrategyCalculationContext.Sku(
                        100L, "SKU-100", "상품", "EA", BigDecimal.ONE
                ),
                decimal("50"),
                new StrategyCalculationContext.RequestConstraints(
                        List.of(), List.of(), null, null
                ),
                lots, lots, List.of(), Map.of(10L, salesPoint),
                new StrategyCalculationContext.ForecastMetadata(
                        "forecast-1", 1L,
                        OffsetDateTime.of(
                                2026, 8, 20, 8, 0, 0, 0,
                                ZoneOffset.ofHours(9)
                        )
                )
        );
    }

    private static StrategyCalculationContext.InventoryLot lot(
            Long inventoryBalanceId,
            Long lotId,
            BigDecimal availableQty,
            LocalDate expiryDate
    ) {
        return new StrategyCalculationContext.InventoryLot(
                inventoryBalanceId,
                lotId,
                501L,
                10L,
                10L,
                availableQty,
                BigDecimal.ZERO,
                null,
                START.minusDays(10),
                expiryDate,
                null,
                "AVAILABLE"
        );
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
