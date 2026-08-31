package com.stockit.backend.feature.strategy.calculation.candidate.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stockit.backend.feature.strategy.calculation.candidate.calculator.StrategyCandidateCalculator;
import com.stockit.backend.feature.strategy.calculation.candidate.domain.CandidateExclusionReason;
import com.stockit.backend.feature.strategy.calculation.candidate.domain.CandidateGenerationResult;
import com.stockit.backend.feature.strategy.calculation.candidate.domain.StrategyCandidate;
import com.stockit.backend.feature.strategy.calculation.candidate.policy.StrategyPeriodEligibilityPolicy;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.domain.StrategyType;

@ExtendWith(MockitoExtension.class)
class StrategyCandidateGenerationServiceImplTest {

    @Mock
    private StrategyCandidateCalculator reallocationCalculator;
    @Mock
    private StrategyCandidateCalculator transferCalculator;
    @Mock
    private StrategyCandidateCalculator discountCalculator;
    @Mock
    private StrategyCandidateCalculator expansionCalculator;
    @Mock
    private StrategyCandidateCalculator concentrationCalculator;

    @Test
    void preservesOriginalUserTypePriorityWhenEarlierTypeIsNotYetImplemented() {
        when(reallocationCalculator.supportedType()).thenReturn(StrategyType.REALLOCATION);
        when(transferCalculator.supportedType()).thenReturn(StrategyType.RT_TRANSFER);
        StrategyCandidateGenerationServiceImpl service =
                new StrategyCandidateGenerationServiceImpl(List.of(
                        reallocationCalculator,
                        transferCalculator
                ), new StrategyPeriodEligibilityPolicy());
        StrategyCalculationContext context = context(List.of(
                StrategyType.PRICE_DISCOUNT,
                StrategyType.RT_TRANSFER
        ));
        when(transferCalculator.generate(context, 2)).thenReturn(
                new CandidateGenerationResult(List.of(), List.of())
        );

        CandidateGenerationResult result = service.generate(context);

        assertThat(result.candidates()).isEmpty();
        verify(transferCalculator).generate(context, 2);
        verify(reallocationCalculator, never()).generate(context, 1);
    }

    @Test
    void generatesFiveSupportedTypesInDefaultPriorityOrder() {
        when(reallocationCalculator.supportedType()).thenReturn(StrategyType.REALLOCATION);
        when(transferCalculator.supportedType()).thenReturn(StrategyType.RT_TRANSFER);
        when(discountCalculator.supportedType()).thenReturn(StrategyType.PRICE_DISCOUNT);
        when(expansionCalculator.supportedType()).thenReturn(StrategyType.CHANNEL_EXPANSION);
        when(concentrationCalculator.supportedType()).thenReturn(
                StrategyType.CHANNEL_CONCENTRATION
        );
        StrategyCandidateGenerationServiceImpl service =
                new StrategyCandidateGenerationServiceImpl(List.of(
                        reallocationCalculator,
                        transferCalculator,
                        discountCalculator,
                        expansionCalculator,
                        concentrationCalculator
                ), new StrategyPeriodEligibilityPolicy());
        StrategyCalculationContext context = context(List.of());
        CandidateGenerationResult empty = new CandidateGenerationResult(
                List.of(), List.of()
        );
        when(reallocationCalculator.generate(context, 1)).thenReturn(empty);
        when(transferCalculator.generate(context, 2)).thenReturn(empty);
        when(discountCalculator.generate(context, 3)).thenReturn(empty);
        when(expansionCalculator.generate(context, 4)).thenReturn(empty);
        when(concentrationCalculator.generate(context, 5)).thenReturn(empty);

        service.generate(context);

        verify(reallocationCalculator).generate(context, 1);
        verify(transferCalculator).generate(context, 2);
        verify(discountCalculator).generate(context, 3);
        verify(expansionCalculator).generate(context, 4);
        verify(concentrationCalculator).generate(context, 5);
    }

    @Test
    void generatesAllocationAndTransferByDefaultForPublicUnassignedInventory() {
        stubAllSupportedTypes();
        StrategyCandidateGenerationServiceImpl service = serviceWithAllCalculators();
        StrategyCalculationContext context = context(List.of(), null);
        CandidateGenerationResult empty = new CandidateGenerationResult(
                List.of(), List.of()
        );
        when(reallocationCalculator.generate(context, 1)).thenReturn(empty);
        when(transferCalculator.generate(context, 2)).thenReturn(empty);

        CandidateGenerationResult result = service.generate(context);

        assertThat(result.exclusions()).isEmpty();
        verify(reallocationCalculator).generate(context, 1);
        verify(transferCalculator).generate(context, 2);
        verify(discountCalculator, never()).generate(context, 3);
        verify(expansionCalculator, never()).generate(context, 4);
        verify(concentrationCalculator, never()).generate(context, 5);
    }

    @Test
    void allowsExplicitTransferForPublicUnassignedInventory() {
        when(reallocationCalculator.supportedType()).thenReturn(StrategyType.REALLOCATION);
        when(transferCalculator.supportedType()).thenReturn(StrategyType.RT_TRANSFER);
        StrategyCandidateGenerationServiceImpl service =
                new StrategyCandidateGenerationServiceImpl(List.of(
                        reallocationCalculator,
                        transferCalculator
                ), new StrategyPeriodEligibilityPolicy());
        StrategyCalculationContext context = context(
                List.of(StrategyType.RT_TRANSFER),
                null
        );
        CandidateGenerationResult empty = new CandidateGenerationResult(
                List.of(), List.of()
        );
        when(transferCalculator.generate(context, 1)).thenReturn(empty);

        CandidateGenerationResult result = service.generate(context);

        assertThat(result.candidates()).isEmpty();
        assertThat(result.exclusions()).isEmpty();
        verify(transferCalculator).generate(context, 1);
        verify(reallocationCalculator, never()).generate(context, 1);
    }

    @Test
    void excludesExplicitSellingTypeForPublicUnassignedInventory() {
        when(reallocationCalculator.supportedType()).thenReturn(StrategyType.REALLOCATION);
        when(transferCalculator.supportedType()).thenReturn(StrategyType.RT_TRANSFER);
        when(discountCalculator.supportedType()).thenReturn(StrategyType.PRICE_DISCOUNT);
        StrategyCandidateGenerationServiceImpl service =
                new StrategyCandidateGenerationServiceImpl(List.of(
                        reallocationCalculator,
                        transferCalculator,
                        discountCalculator
                ), new StrategyPeriodEligibilityPolicy());
        StrategyCalculationContext context = context(
                List.of(StrategyType.PRICE_DISCOUNT),
                null
        );

        CandidateGenerationResult result = service.generate(context);

        assertThat(result.exclusions()).singleElement().satisfies(exclusion -> {
            assertThat(exclusion.strategyType()).isEqualTo(StrategyType.PRICE_DISCOUNT);
            assertThat(exclusion.reason()).isEqualTo(
                    CandidateExclusionReason.PUBLIC_UNASSIGNED_STRATEGY_NOT_SUPPORTED
            );
        });
        verify(discountCalculator, never()).generate(context, 1);
        verify(reallocationCalculator, never()).generate(context, 1);
        verify(transferCalculator, never()).generate(context, 1);
    }

    @Test
    void excludesCandidateWhoseAllocatedLotsCannotBeSoldThroughItsEndDate() {
        when(discountCalculator.supportedType()).thenReturn(
                StrategyType.PRICE_DISCOUNT
        );
        StrategyCandidateGenerationServiceImpl service =
                new StrategyCandidateGenerationServiceImpl(
                        List.of(discountCalculator),
                        new StrategyPeriodEligibilityPolicy()
                );
        StrategyCalculationContext context = context(
                List.of(StrategyType.PRICE_DISCOUNT),
                10L,
                LocalDate.of(2026, 8, 20)
        );
        StrategyCandidate candidate = discountCandidate(
                LocalDate.of(2026, 8, 21)
        );
        when(discountCalculator.generate(context, 1)).thenReturn(
                new CandidateGenerationResult(List.of(candidate), List.of())
        );

        CandidateGenerationResult result = service.generate(context);

        assertThat(result.candidates()).isEmpty();
        assertThat(result.exclusions()).singleElement().satisfies(exclusion -> {
            assertThat(exclusion.reason()).isEqualTo(
                    CandidateExclusionReason.LOT_NOT_SELLABLE_IN_PERIOD
            );
            assertThat(exclusion.targetSalesPointId()).isEqualTo(10L);
        });
    }

    private void stubAllSupportedTypes() {
        when(reallocationCalculator.supportedType()).thenReturn(StrategyType.REALLOCATION);
        when(transferCalculator.supportedType()).thenReturn(StrategyType.RT_TRANSFER);
        when(discountCalculator.supportedType()).thenReturn(StrategyType.PRICE_DISCOUNT);
        when(expansionCalculator.supportedType()).thenReturn(StrategyType.CHANNEL_EXPANSION);
        when(concentrationCalculator.supportedType()).thenReturn(
                StrategyType.CHANNEL_CONCENTRATION
        );
    }

    private StrategyCandidateGenerationServiceImpl serviceWithAllCalculators() {
        return new StrategyCandidateGenerationServiceImpl(List.of(
                reallocationCalculator,
                transferCalculator,
                discountCalculator,
                expansionCalculator,
                concentrationCalculator
        ), new StrategyPeriodEligibilityPolicy());
    }

    private static StrategyCalculationContext context(List<StrategyType> types) {
        return context(types, 10L);
    }

    private static StrategyCalculationContext context(
            List<StrategyType> types,
            Long sourceSalesPointId
    ) {
        return context(types, sourceSalesPointId, null);
    }

    private static StrategyCalculationContext context(
            List<StrategyType> types,
            Long sourceSalesPointId,
            LocalDate expiryDate
    ) {
        StrategyCalculationContext.InventoryLot inventory =
                new StrategyCalculationContext.InventoryLot(
                        1L,
                        1001L,
                        501L,
                        sourceSalesPointId,
                        sourceSalesPointId,
                        decimal("10"),
                        BigDecimal.ZERO,
                        null,
                        LocalDate.of(2026, 8, 1),
                        expiryDate,
                        null,
                        "AVAILABLE"
                );
        return new StrategyCalculationContext(
                12345L,
                sourceSalesPointId,
                LocalDateTime.of(2026, 8, 20, 10, 0),
                LocalDate.of(2026, 8, 20),
                LocalDate.of(2026, 8, 21),
                new StrategyCalculationContext.Sku(
                        101L, "SKU-101", "테스트 SKU", "EA", BigDecimal.ONE
                ),
                decimal("50"),
                new StrategyCalculationContext.RequestConstraints(
                        List.of(20L),
                        types,
                        null,
                        null
                ),
                List.of(inventory),
                List.of(inventory),
                List.of(),
                new LinkedHashMap<>(),
                new StrategyCalculationContext.ForecastMetadata(
                        "forecast-run-1",
                        3L,
                        OffsetDateTime.of(
                                2026, 8, 20, 9, 0, 0, 0,
                                ZoneOffset.ofHours(9)
                        )
                )
        );
    }

    private static StrategyCandidate discountCandidate(LocalDate endDate) {
        StrategyCandidate.Location location = new StrategyCandidate.Location(
                501L, 10L
        );
        return new StrategyCandidate(
                "PRICE-DISCOUNT-1",
                List.of(StrategyType.PRICE_DISCOUNT),
                LocalDate.of(2026, 8, 20),
                endDate,
                List.of(new StrategyCandidate.Action(
                        StrategyType.PRICE_DISCOUNT,
                        location,
                        location,
                        decimal("10"),
                        BigDecimal.ZERO,
                        decimal("90"),
                        decimal("0.10"),
                        List.of(new StrategyCandidate.LotAllocation(
                                1L, 1001L, decimal("10"), 1
                        ))
                )),
                List.of(),
                new StrategyCandidate.Preference(1, 1, 100),
                new StrategyCandidate.DiscountEvidence(
                        decimal("10"), decimal("10"), decimal("10"),
                        decimal("10"), decimal("100"), decimal("70")
                )
        );
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
