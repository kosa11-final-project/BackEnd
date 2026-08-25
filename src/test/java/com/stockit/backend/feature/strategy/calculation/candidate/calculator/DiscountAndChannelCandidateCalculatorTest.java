package com.stockit.backend.feature.strategy.calculation.candidate.calculator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.stockit.backend.feature.strategy.calculation.candidate.domain.CandidateAssumption;
import com.stockit.backend.feature.strategy.calculation.candidate.domain.CandidateExclusionReason;
import com.stockit.backend.feature.strategy.calculation.candidate.domain.CandidateGenerationResult;
import com.stockit.backend.feature.strategy.calculation.candidate.domain.StrategyCandidate;
import com.stockit.backend.feature.strategy.calculation.candidate.domain.StrategyCandidateIdGenerator;
import com.stockit.backend.feature.strategy.calculation.candidate.policy.DiscountRateCandidatePolicy;
import com.stockit.backend.feature.strategy.calculation.candidate.policy.MovementLotAllocationPolicy;
import com.stockit.backend.feature.strategy.calculation.candidate.policy.SafetyStockPolicyResolver;
import com.stockit.backend.feature.strategy.calculation.candidate.policy.SourceInventoryCapacityPolicy;
import com.stockit.backend.feature.strategy.calculation.candidate.policy.StrategyPeriodCandidatePolicy;
import com.stockit.backend.feature.strategy.calculation.candidate.policy.StrategyPeriodEligibilityPolicy;
import com.stockit.backend.feature.strategy.calculation.candidate.policy.TargetAdditionalDemandPolicy;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.calculation.policy.DiscountSimulationProperties;
import com.stockit.backend.feature.strategy.calculation.policy.DiscountDemandPolicy;
import com.stockit.backend.feature.strategy.calculation.policy.SalesPointDiscountPolicy;
import com.stockit.backend.feature.strategy.domain.StrategyType;
import com.stockit.backend.feature.strategy.service.StrategyDateTimeProvider;

class DiscountAndChannelCandidateCalculatorTest {

    private static final LocalDate START = LocalDate.of(2026, 8, 20);
    private static final LocalDate END = LocalDate.of(2026, 8, 21);

    private PriceDiscountCandidateCalculator discountCalculator;
    private ChannelExpansionCandidateCalculator expansionCalculator;
    private ChannelConcentrationCandidateCalculator concentrationCalculator;
    private MovementLotAllocationPolicy allocationPolicy;

    @BeforeEach
    void setUp() {
        SafetyStockPolicyResolver safetyResolver = new SafetyStockPolicyResolver();
        SourceInventoryCapacityPolicy capacityPolicy =
                new SourceInventoryCapacityPolicy(safetyResolver);
        allocationPolicy = spy(new MovementLotAllocationPolicy());
        StrategyCandidateIdGenerator idGenerator =
                new StrategyCandidateIdGenerator();
        StrategyDateTimeProvider dateTimeProvider = mock(
                StrategyDateTimeProvider.class
        );
        when(dateTimeProvider.now()).thenReturn(START.atStartOfDay());
        StrategyPeriodCandidatePolicy periodPolicy =
                new StrategyPeriodCandidatePolicy(
                        new StrategyPeriodEligibilityPolicy(),
                        dateTimeProvider
                );
        InventoryMovementCandidateFactory movementFactory =
                new InventoryMovementCandidateFactory(
                        capacityPolicy,
                        new TargetAdditionalDemandPolicy(),
                        allocationPolicy,
                        idGenerator,
                        new InventoryTransferCostCalculator()
                );
        DiscountSimulationProperties discountProperties =
                new DiscountSimulationProperties();
        SalesPointDiscountPolicy salesPointDiscountPolicy =
                new SalesPointDiscountPolicy(discountProperties);
        discountCalculator = new PriceDiscountCandidateCalculator(
                new DiscountRateCandidatePolicy(
                        salesPointDiscountPolicy
                ),
                periodPolicy,
                capacityPolicy,
                allocationPolicy,
                idGenerator,
                new DiscountDemandPolicy(
                        discountProperties,
                        salesPointDiscountPolicy
                )
        );
        expansionCalculator = new ChannelExpansionCandidateCalculator(
                movementFactory,
                idGenerator,
                periodPolicy
        );
        concentrationCalculator = new ChannelConcentrationCandidateCalculator(
                movementFactory,
                idGenerator,
                periodPolicy
        );
    }

    @Test
    void generatesFivePercentDiscountStepsForSimulation() {
        StrategyCalculationContext context = context(
                null,
                List.of(StrategyType.PRICE_DISCOUNT)
        );

        CandidateGenerationResult result = discountCalculator.generate(context, 1);

        assertThat(result.exclusions()).isEmpty();
        assertThat(result.candidates()).hasSize(20);
        assertThat(result.candidates())
                .extracting(candidate -> candidate.actions().get(0).discountRate())
                .containsOnly(decimal("0.0500"), decimal("0.1000"));
        StrategyCandidate tenPercentMaximum = result.candidates().stream()
                .filter(candidate -> candidate.actions().get(0).discountRate()
                        .compareTo(decimal("0.1000")) == 0)
                .filter(candidate -> candidate.preference().quantityPercentage() == 100)
                .findFirst()
                .orElseThrow();
        assertThat(tenPercentMaximum.actions().get(0).strategyPrice())
                .isEqualByComparingTo("90");
        assertThat(tenPercentMaximum.evidence())
                .isInstanceOf(StrategyCandidate.DiscountEvidence.class);
        assertThat(tenPercentMaximum.assumptions()).isEmpty();
        assertThat(tenPercentMaximum.actions().get(0).actionQuantity())
                .isEqualByComparingTo("11");
        verify(allocationPolicy, times(22)).plan(any(), any(), any(), any());
    }

    @Test
    void roundsDiscountCandidateQuantitiesDownToWholeUnits() {
        StrategyCalculationContext context = context(
                null,
                List.of(StrategyType.PRICE_DISCOUNT),
                "15",
                "7.5"
        );

        CandidateGenerationResult result = discountCalculator.generate(context, 1);

        assertThat(result.candidates().stream()
                .filter(candidate -> candidate.actions().get(0).discountRate()
                        .compareTo(decimal("0.0500")) == 0)
                .map(candidate -> candidate.actions().get(0).actionQuantity())
                .toList())
                .containsExactly(
                        decimal("1.000"), decimal("3.000"), decimal("4.000"),
                        decimal("6.000"), decimal("7.000"), decimal("9.000"),
                        decimal("10.000"), decimal("12.000"), decimal("13.000"),
                        decimal("15.000")
                );
    }

    @Test
    void limitsFutureDiscountQuantityToProjectedRemainingInventory() {
        StrategyCalculationContext original = context(
                null,
                List.of(StrategyType.PRICE_DISCOUNT),
                "20",
                "5"
        );
        StrategyCalculationContext context = withRequestPeriod(
                replaceSalesPoint(
                        original,
                        salesPoint(
                                10L,
                                price(10L, "100", "90"),
                                forecasts("5", "20"),
                                List.of(route(10L, 501L))
                        )
                ),
                END,
                END
        );

        CandidateGenerationResult result = discountCalculator.generate(context, 1);

        StrategyCandidate maximum = result.candidates().stream()
                .filter(candidate -> candidate.preference().quantityPercentage() == 100)
                .findFirst()
                .orElseThrow();
        assertThat(maximum.actions().get(0).actionQuantity())
                .isEqualByComparingTo("15");
        assertThat(maximum.evidence().maxExecutableQty())
                .isEqualByComparingTo("15");
    }

    @Test
    void expandsOnlyToUnlistedTargetUsingCopiedSourceCommercialTerms() {
        StrategyCalculationContext context = context(
                null,
                List.of(StrategyType.CHANNEL_EXPANSION)
        );

        CandidateGenerationResult result = expansionCalculator.generate(context, 1);

        assertThat(result.candidates()).hasSize(10);
        StrategyCandidate maximum = result.candidates().stream()
                .filter(candidate -> candidate.preference().quantityPercentage() == 100)
                .findFirst()
                .orElseThrow();
        assertThat(maximum.endDate()).isEqualTo(END);
        assertThat(maximum.strategyTypes()).containsExactly(
                StrategyType.CHANNEL_EXPANSION,
                StrategyType.REALLOCATION
        );
        assertThat(maximum.actions())
                .extracting(StrategyCandidate.Action::actionType)
                .containsExactly(
                        StrategyType.REALLOCATION,
                        StrategyType.CHANNEL_EXPANSION
                );
        assertThat(maximum.actions().get(1).strategyPrice())
                .isEqualByComparingTo("100");
        assertThat(maximum.assumptions()).containsExactly(
                CandidateAssumption.TARGET_COMMERCIAL_TERMS_COPIED_FROM_SOURCE
        );
        StrategyCandidate.ChannelEvidence evidence =
                (StrategyCandidate.ChannelEvidence) maximum.evidence();
        assertThat(evidence.paymentFee()).isEqualByComparingTo("5");
        assertThat(evidence.logisticsCost()).isEqualByComparingTo("10");
    }

    @Test
    void expandsThroughPhysicalTransferWhenTargetUsesAnotherWarehouse() {
        StrategyCalculationContext original = context(
                null,
                List.of(StrategyType.CHANNEL_EXPANSION)
        );
        StrategyCalculationContext context = replaceTarget(
                original,
                salesPoint(
                        20L,
                        null,
                        forecasts("10"),
                        List.of(route(20L, 502L))
                )
        );

        CandidateGenerationResult result = expansionCalculator.generate(context, 1);

        assertThat(result.candidates()).hasSize(10);
        assertThat(result.candidates()).allSatisfy(candidate -> {
            assertThat(candidate.strategyTypes()).containsExactly(
                    StrategyType.CHANNEL_EXPANSION,
                    StrategyType.RT_TRANSFER
            );
            assertThat(candidate.assumptions()).containsExactlyInAnyOrder(
                    CandidateAssumption.TARGET_COMMERCIAL_TERMS_COPIED_FROM_SOURCE
            );
            assertThat(candidate.actions().get(0).target().warehouseId())
                    .isEqualTo(502L);
        });
    }

    @Test
    void concentratesEachCandidateOnOneCurrentlyListedTarget() {
        StrategyCalculationContext context = context(
                price(20L, "110", "90"),
                List.of(StrategyType.CHANNEL_CONCENTRATION)
        );

        CandidateGenerationResult result = concentrationCalculator.generate(context, 1);

        assertThat(result.candidates()).hasSize(10);
        assertThat(result.candidates()).allSatisfy(candidate -> {
            assertThat(candidate.strategyTypes()).containsExactly(
                    StrategyType.CHANNEL_CONCENTRATION,
                    StrategyType.REALLOCATION
            );
            assertThat(candidate.actions().get(1).target().salesPointId())
                    .isEqualTo(20L);
            assertThat(candidate.assumptions()).doesNotContain(
                    CandidateAssumption.TARGET_COMMERCIAL_TERMS_COPIED_FROM_SOURCE
            );
        });
    }

    @Test
    void rejectsListedTargetForExpansionAndUnlistedTargetForConcentration() {
        CandidateGenerationResult expansion = expansionCalculator.generate(
                context(price(20L, "110", "90"),
                        List.of(StrategyType.CHANNEL_EXPANSION)),
                1
        );
        CandidateGenerationResult concentration = concentrationCalculator.generate(
                context(null, List.of(StrategyType.CHANNEL_CONCENTRATION)),
                1
        );

        assertThat(expansion.candidates()).isEmpty();
        assertThat(expansion.exclusions()).singleElement().satisfies(exclusion ->
                assertThat(exclusion.reason()).isEqualTo(
                        CandidateExclusionReason.TARGET_ALREADY_LISTED
                ));
        assertThat(concentration.candidates()).isEmpty();
        assertThat(concentration.exclusions()).singleElement().satisfies(exclusion ->
                assertThat(exclusion.reason()).isEqualTo(
                        CandidateExclusionReason.TARGET_NOT_LISTED
                ));
    }

    @Test
    void doesNotTreatListedTargetWithIncompleteTermsAsExpansionTarget() {
        StrategyCalculationContext original = context(
                null,
                List.of(StrategyType.CHANNEL_EXPANSION)
        );
        StrategyCalculationContext.SalesPoint incompleteListedTarget =
                new StrategyCalculationContext.SalesPoint(
                        20L,
                        "SP-20",
                        "판매처 20",
                        BigDecimal.ZERO,
                        true,
                        null,
                        forecasts("10"),
                        List.of(route(20L, 501L))
                );
        StrategyCalculationContext context = replaceTarget(
                original,
                incompleteListedTarget
        );

        CandidateGenerationResult result = expansionCalculator.generate(context, 1);

        assertThat(result.candidates()).isEmpty();
        assertThat(result.exclusions()).singleElement().satisfies(exclusion ->
                assertThat(exclusion.reason()).isEqualTo(
                        CandidateExclusionReason.TARGET_ALREADY_LISTED
                ));
    }

    @Test
    void excludesListedConcentrationTargetWithIncompleteCommercialTerms() {
        StrategyCalculationContext original = context(
                null,
                List.of(StrategyType.CHANNEL_CONCENTRATION)
        );
        StrategyCalculationContext.SalesPoint incompleteListedTarget =
                new StrategyCalculationContext.SalesPoint(
                        20L,
                        "SP-20",
                        "판매처 20",
                        BigDecimal.ZERO,
                        true,
                        null,
                        forecasts("10"),
                        List.of(route(20L, 501L))
                );
        StrategyCalculationContext context = replaceTarget(
                original,
                incompleteListedTarget
        );

        CandidateGenerationResult result = concentrationCalculator.generate(context, 1);

        assertThat(result.candidates()).isEmpty();
        assertThat(result.exclusions()).singleElement().satisfies(exclusion ->
                assertThat(exclusion.reason()).isEqualTo(
                        CandidateExclusionReason.TARGET_PRICE_INCOMPLETE
                ));
    }

    private static StrategyCalculationContext replaceTarget(
            StrategyCalculationContext original,
            StrategyCalculationContext.SalesPoint target
    ) {
        return replaceSalesPoint(original, target);
    }

    private static StrategyCalculationContext replaceSalesPoint(
            StrategyCalculationContext original,
            StrategyCalculationContext.SalesPoint salesPoint
    ) {
        Map<Long, StrategyCalculationContext.SalesPoint> salesPoints =
                new LinkedHashMap<>(original.salesPoints());
        salesPoints.put(salesPoint.salesPointId(), salesPoint);
        return new StrategyCalculationContext(
                original.strategyCaseId(),
                original.sourceSalesPointId(),
                original.calculatedAt(),
                original.forecastStartDate(),
                original.forecastEndDate(),
                original.sku(),
                original.unitCost(),
                original.requestConstraints(),
                original.evaluationInventory(),
                original.referenceInventory(),
                original.inventoryPolicies(),
                salesPoints,
                original.forecastMetadata(),
                original.transferRoutes(),
                original.transferCostPolicies()
        );
    }

    private static StrategyCalculationContext withRequestPeriod(
            StrategyCalculationContext original,
            LocalDate preferredStartDate,
            LocalDate preferredEndDate
    ) {
        return new StrategyCalculationContext(
                original.strategyCaseId(),
                original.sourceSalesPointId(),
                original.calculatedAt(),
                original.forecastStartDate(),
                original.forecastEndDate(),
                original.sku(),
                original.unitCost(),
                new StrategyCalculationContext.RequestConstraints(
                        original.requestConstraints().orderedCandidateSalesPointIds(),
                        original.requestConstraints().orderedStrategyTypes(),
                        preferredStartDate,
                        preferredEndDate
                ),
                original.evaluationInventory(),
                original.referenceInventory(),
                original.inventoryPolicies(),
                original.salesPoints(),
                original.forecastMetadata(),
                original.transferRoutes(),
                original.transferCostPolicies()
        );
    }

    private static StrategyCalculationContext context(
            StrategyCalculationContext.Price targetPrice,
            List<StrategyType> requestedTypes
    ) {
        return context(targetPrice, requestedTypes, "100", "5");
    }

    private static StrategyCalculationContext context(
            StrategyCalculationContext.Price targetPrice,
            List<StrategyType> requestedTypes,
            String sourceInventoryQuantity,
            String sourceDailyForecast
    ) {
        StrategyCalculationContext.InventoryLot sourceInventory =
                new StrategyCalculationContext.InventoryLot(
                        1L,
                        1001L,
                        501L,
                        10L,
                        10L,
                        decimal(sourceInventoryQuantity),
                        BigDecimal.ZERO,
                        null,
                        LocalDate.of(2026, 8, 1),
                        null,
                        null,
                        "AVAILABLE"
                );
        Map<Long, StrategyCalculationContext.SalesPoint> salesPoints =
                new LinkedHashMap<>();
        salesPoints.put(10L, salesPoint(
                10L,
                price(10L, "100", "90"),
                forecasts(sourceDailyForecast),
                List.of(route(10L, 501L))
        ));
        salesPoints.put(20L, salesPoint(
                20L,
                targetPrice,
                forecasts("10"),
                List.of(route(20L, 501L))
        ));
        return new StrategyCalculationContext(
                12345L,
                10L,
                LocalDateTime.of(2026, 8, 20, 10, 0),
                START,
                END,
                new StrategyCalculationContext.Sku(
                        101L, "SKU-101", "테스트 SKU", "EA", BigDecimal.ONE,
                        new BigDecimal("0.5"), "KG"
                ),
                decimal("50"),
                new StrategyCalculationContext.RequestConstraints(
                        List.of(20L),
                        requestedTypes,
                        START,
                        END
                ),
                List.of(sourceInventory),
                List.of(sourceInventory),
                List.of(new StrategyCalculationContext.InventoryPolicy(
                        1L, 501L, 10L, 10L, BigDecimal.ZERO,
                        null, null, null
                )),
                salesPoints,
                new StrategyCalculationContext.ForecastMetadata(
                        "forecast-run-1",
                        3L,
                        OffsetDateTime.of(
                                2026, 8, 20, 9, 0, 0, 0,
                                ZoneOffset.ofHours(9)
                        )
                ),
                List.of(new StrategyCalculationContext.TransferRoute(
                        9001L,
                        new StrategyCalculationContext.PhysicalLocation(501L, null),
                        new StrategyCalculationContext.PhysicalLocation(502L, null),
                        new BigDecimal("100"),
                        "DUMMY",
                        null,
                        null
                )),
                List.of(new StrategyCalculationContext.TransferCostPolicy(
                        8001L,
                        "DUMMY-COMMON",
                        new BigDecimal("2"),
                        START.minusDays(1),
                        null
                ))
        );
    }

    private static StrategyCalculationContext.SalesPoint salesPoint(
            Long id,
            StrategyCalculationContext.Price price,
            Map<LocalDate, BigDecimal> forecast,
            List<StrategyCalculationContext.WarehouseRoute> routes
    ) {
        return new StrategyCalculationContext.SalesPoint(
                id,
                "SP-" + id,
                "판매처 " + id,
                BigDecimal.ZERO,
                price,
                forecast,
                routes
        );
    }

    private static StrategyCalculationContext.Price price(
            Long id,
            String actualPrice,
            String minimumPrice
    ) {
        return new StrategyCalculationContext.Price(
                id * 10,
                decimal("120"),
                decimal(actualPrice),
                decimal(minimumPrice),
                decimal("5"),
                decimal("10")
        );
    }

    private static StrategyCalculationContext.WarehouseRoute route(
            Long salesPointId,
            Long warehouseId
    ) {
        return new StrategyCalculationContext.WarehouseRoute(
                salesPointId * 1000 + warehouseId,
                salesPointId,
                warehouseId,
                1,
                null
        );
    }

    private static Map<LocalDate, BigDecimal> forecasts(String quantity) {
        Map<LocalDate, BigDecimal> forecasts = new LinkedHashMap<>();
        for (LocalDate date = START; !date.isAfter(END); date = date.plusDays(1)) {
            forecasts.put(date, decimal(quantity));
        }
        return forecasts;
    }

    private static Map<LocalDate, BigDecimal> forecasts(
            String first,
            String second
    ) {
        Map<LocalDate, BigDecimal> forecasts = new LinkedHashMap<>();
        forecasts.put(START, decimal(first));
        forecasts.put(END, decimal(second));
        return forecasts;
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
