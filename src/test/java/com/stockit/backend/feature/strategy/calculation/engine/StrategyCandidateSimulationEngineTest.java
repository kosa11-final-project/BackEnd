package com.stockit.backend.feature.strategy.calculation.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.stockit.backend.feature.strategy.calculation.candidate.domain.StrategyCandidate;
import com.stockit.backend.feature.strategy.calculation.candidate.policy.SafetyStockPolicyResolver;
import com.stockit.backend.feature.strategy.calculation.candidate.policy.SourceInventoryCapacityPolicy;
import com.stockit.backend.feature.strategy.calculation.candidate.policy.TargetAdditionalDemandPolicy;
import com.stockit.backend.feature.strategy.calculation.domain.BaselineSimulation;
import com.stockit.backend.feature.strategy.calculation.domain.SimulationDetailLevel;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCandidateSimulation;
import com.stockit.backend.feature.strategy.domain.StrategyType;

class StrategyCandidateSimulationEngineTest {

    private static final LocalDate START = LocalDate.of(2026, 8, 20);
    private static final LocalDate END = LocalDate.of(2026, 8, 22);

    private BaselineSimulationEngine baselineEngine;
    private StrategyCandidateSimulationEngine engine;

    @BeforeEach
    void setUp() {
        baselineEngine = new BaselineSimulationEngine();
        engine = new StrategyCandidateSimulationEngine(
                new TargetAdditionalDemandPolicy(),
                new SourceInventoryCapacityPolicy(new SafetyStockPolicyResolver())
        );
    }

    @Test
    void simulatesDiscountThenReturnsToNormalPriceAfterStrategyEnd() {
        StrategyCalculationContext context = context(
                lot("10", null),
                forecasts("2", "2", "2"),
                forecasts("0", "0", "0"),
                price(10L, "100", "70"),
                price(20L, "110", "70"),
                true
        );
        StrategyCandidate candidate = discountCandidate(
                "6",
                START,
                START.plusDays(1),
                "80",
                "0.2000"
        );
        BaselineSimulation baseline = baselineEngine.simulate(context);

        StrategyCandidateSimulation result = engine.simulate(
                context,
                candidate,
                baseline,
                SimulationDetailLevel.WITH_DAILY_SERIES
        );

        assertThat(result.summary().expectedSalesQty()).isEqualByComparingTo("6");
        assertThat(result.summary().expectedRevenue()).isEqualByComparingTo("520");
        assertThat(result.summary().totalContributionMargin())
                .isEqualByComparingTo("130");
        assertThat(result.summary().expectedRemainingQty())
                .isEqualByComparingTo("4");
        assertThat(result.summary().expectedSellThroughDays()).isEqualTo(3);
        assertThat(result.comparisonToBaseline().revenueDelta())
                .isEqualByComparingTo("-80");
        assertThat(result.comparisonToBaseline().netEffect())
                .isEqualByComparingTo("-80");
        assertThat(result.dailySeries()).hasSize(3);
        assertThat(result.dailySeries().get(1).cumulativeRevenue())
                .isEqualByComparingTo("320");
        assertThat(result.dailySeries().get(2).cumulativeRevenue())
                .isEqualByComparingTo("520");
    }

    @Test
    void keepsStandaloneMovementEffectiveWithoutEndDate() {
        StrategyCalculationContext context = context(
                lot("10", null),
                forecasts("0", "0", "0"),
                forecasts("5", "5", "5"),
                price(10L, "100", "70"),
                price(20L, "110", "70"),
                true
        );
        StrategyCandidate candidate = movementCandidate(
                "10",
                START,
                null,
                StrategyType.REALLOCATION
        );

        StrategyCandidateSimulation result = engine.simulate(
                context,
                candidate,
                baselineEngine.simulate(context),
                SimulationDetailLevel.WITH_DAILY_SERIES
        );

        assertThat(candidate.endDate()).isNull();
        assertThat(result.summary().expectedSalesQty()).isEqualByComparingTo("10");
        assertThat(result.summary().expectedRevenue()).isEqualByComparingTo("1100");
        assertThat(result.summary().totalContributionMargin())
                .isEqualByComparingTo("450");
        assertThat(result.summary().expectedSellThroughDays()).isEqualTo(2);
        assertThat(result.dailySeries())
                .extracting(StrategyCandidateSimulation.DailyPoint::expectedSalesQty)
                .containsExactly(decimal("5.000"), decimal("5.000"), decimal("0.000"));
    }

    @Test
    void fillsTargetDemandOutsideRequestedStrategyPeriodWithZero() {
        StrategyCalculationContext original = context(
                lot("10", null),
                forecasts("0", "0", "0"),
                forecasts("5", "5", "5"),
                price(10L, "100", "70"),
                price(20L, "110", "70"),
                true
        );
        StrategyCalculationContext context = withRequestPeriod(
                original,
                START.plusDays(1),
                START.plusDays(1)
        );

        StrategyCandidateSimulation result = engine.simulate(
                context,
                movementCandidate(
                        "10",
                        START.plusDays(1),
                        null,
                        StrategyType.REALLOCATION
                ),
                baselineEngine.simulate(context),
                SimulationDetailLevel.WITH_DAILY_SERIES
        );

        assertThat(result.dailySeries())
                .extracting(StrategyCandidateSimulation.DailyPoint::expectedSalesQty)
                .containsExactly(decimal("0.000"), decimal("5.000"), decimal("0.000"));
    }

    @Test
    void isolatesInvalidTargetDemandFromIndependentCandidateSimulation() {
        StrategyCalculationContext context = context(
                lot("10", null),
                forecasts("0", "0", "0"),
                forecasts("5", "-1", "5"),
                price(10L, "100", "70"),
                price(20L, "110", "70"),
                true
        );
        BaselineSimulation baseline = baselineEngine.simulate(context);

        assertThatThrownBy(() -> engine.simulate(
                context,
                movementCandidate("10", START, null, StrategyType.REALLOCATION),
                baseline,
                SimulationDetailLevel.SUMMARY_ONLY
        )).isInstanceOfSatisfying(
                CandidateSimulationException.class,
                exception -> {
                    assertThat(exception.getCode())
                            .isEqualTo("CALCULATION_FORECAST_INVALID");
                    assertThat(exception.getMessage())
                            .contains("Target daily forecast");
                }
        );

        StrategyCandidateSimulation independent = engine.simulate(
                context,
                discountCandidate("5", START, END, "80", "0.2000"),
                baseline,
                SimulationDetailLevel.SUMMARY_ONLY
        );

        assertThat(independent.candidateId()).isEqualTo("CAND-DISCOUNT");
        assertThat(independent.summary().expectedSalesQty())
                .isEqualByComparingTo("0");
    }

    @Test
    void leavesSellThroughDaysNullWhenAllocatedInventoryIsDisposed() {
        StrategyCalculationContext context = context(
                lot("5", START),
                forecasts("0", "0", "0"),
                forecasts("2", "0", "0"),
                price(10L, "100", "70"),
                price(20L, "110", "70"),
                true
        );

        StrategyCandidateSimulation result = engine.simulate(
                context,
                movementCandidate("5", START, null, StrategyType.REALLOCATION),
                baselineEngine.simulate(context),
                SimulationDetailLevel.SUMMARY_ONLY
        );

        assertThat(result.summary().expectedSalesQty()).isEqualByComparingTo("2");
        assertThat(result.summary().expectedDisposalQty()).isEqualByComparingTo("3");
        assertThat(result.summary().expectedRemainingQty()).isZero();
        assertThat(result.summary().expectedSellThroughDays()).isNull();
    }

    @Test
    void letsExistingTargetInventoryConsumeDemandBeforeMovedInventory() {
        StrategyCalculationContext original = context(
                lot("10", null),
                forecasts("0", "0", "0"),
                forecasts("5", "5", "5"),
                price(10L, "100", "70"),
                price(20L, "110", "70"),
                true
        );
        StrategyCalculationContext context = withTargetExistingInventory(
                original,
                targetLot("6")
        );

        StrategyCandidateSimulation result = engine.simulate(
                context,
                movementCandidate("10", START, null, StrategyType.REALLOCATION),
                baselineEngine.simulate(context),
                SimulationDetailLevel.WITH_DAILY_SERIES
        );

        assertThat(result.dailySeries())
                .extracting(StrategyCandidateSimulation.DailyPoint::expectedSalesQty)
                .containsExactly(decimal("0.000"), decimal("4.000"), decimal("5.000"));
        assertThat(result.summary().expectedSalesQty()).isEqualByComparingTo("9");
        assertThat(result.summary().expectedRemainingQty()).isEqualByComparingTo("1");
    }

    @Test
    void consumesTargetInventoryBeforeFutureStrategyStart() {
        StrategyCalculationContext original = context(
                lot("10", null),
                forecasts("0", "0", "0"),
                forecasts("5", "5", "5"),
                price(10L, "100", "70"),
                price(20L, "110", "70"),
                true
        );
        StrategyCalculationContext context = withTargetExistingInventory(
                original,
                targetLot("6")
        );

        StrategyCandidateSimulation result = engine.simulate(
                context,
                movementCandidate(
                        "10",
                        START.plusDays(1),
                        null,
                        StrategyType.REALLOCATION
                ),
                baselineEngine.simulate(context),
                SimulationDetailLevel.WITH_DAILY_SERIES
        );

        assertThat(result.dailySeries())
                .extracting(StrategyCandidateSimulation.DailyPoint::expectedSalesQty)
                .containsExactly(decimal("0.000"), decimal("4.000"), decimal("5.000"));
        assertThat(result.summary().expectedSalesQty()).isEqualByComparingTo("9");
        assertThat(result.summary().expectedRemainingQty()).isEqualByComparingTo("1");
    }

    @Test
    void appliesOnlyInventoryRemainingAtFutureStartDate() {
        StrategyCalculationContext context = context(
                lot("10", null),
                forecasts("4", "0", "0"),
                forecasts("0", "3", "3"),
                price(10L, "100", "70"),
                price(20L, "110", "70"),
                true
        );
        StrategyCandidate candidate = movementCandidate(
                "6",
                START.plusDays(1),
                null,
                StrategyType.REALLOCATION
        );

        StrategyCandidateSimulation result = engine.simulate(
                context,
                candidate,
                baselineEngine.simulate(context),
                SimulationDetailLevel.WITH_DAILY_SERIES
        );

        assertThat(result.dailySeries())
                .extracting(StrategyCandidateSimulation.DailyPoint::expectedSalesQty)
                .containsExactly(decimal("4.000"), decimal("3.000"), decimal("3.000"));
        assertThat(result.summary().expectedSellThroughDays()).isEqualTo(2);
        assertThat(result.assumptions()).doesNotContain(
                CandidateAssumption.INVENTORY_RESERVED_UNTIL_STRATEGY_START
        );
    }

    @Test
    void rejectsProjectedInventoryThatExpiresBeforeStrategyStart() {
        StrategyCalculationContext context = context(
                lot("10", START),
                forecasts("0", "0", "0"),
                forecasts("0", "3", "3"),
                price(10L, "100", "70"),
                price(20L, "110", "70"),
                true
        );
        StrategyCandidate candidate = movementCandidate(
                "6",
                START.plusDays(1),
                null,
                StrategyType.REALLOCATION
        );

        assertThatThrownBy(() -> engine.simulate(
                context,
                candidate,
                baselineEngine.simulate(context),
                SimulationDetailLevel.SUMMARY_ONLY
        )).isInstanceOf(CandidateSimulationException.class)
                .hasMessageContaining("Projected candidate inventory is unavailable");
    }

    @Test
    void stopsExpandedChannelSalesAfterCampaignEndButKeepsInventory() {
        StrategyCalculationContext context = context(
                lot("10", null),
                forecasts("0", "0", "0"),
                forecasts("5", "5", "5"),
                price(10L, "100", "70"),
                null,
                false
        );
        StrategyCandidate candidate = channelExpansionCandidate("10", START, START);

        StrategyCandidateSimulation result = engine.simulate(
                context,
                candidate,
                baselineEngine.simulate(context),
                SimulationDetailLevel.WITH_DAILY_SERIES
        );

        assertThat(result.summary().expectedSalesQty()).isEqualByComparingTo("5");
        assertThat(result.summary().expectedRemainingQty()).isEqualByComparingTo("5");
        assertThat(result.summary().expectedSellThroughDays()).isNull();
        assertThat(result.dailySeries())
                .extracting(StrategyCandidateSimulation.DailyPoint::expectedSalesQty)
                .containsExactly(decimal("5.000"), decimal("0.000"), decimal("0.000"));
    }

    @Test
    void simulatesLlmDiscountAddedToChannelMovementCandidate() {
        StrategyCalculationContext context = context(
                lot("10", null),
                forecasts("0", "0", "0"),
                forecasts("5", "5", "5"),
                price(10L, "100", "70"),
                null,
                false
        );
        StrategyCandidate candidate = discountedChannelExpansionCandidate(
                "10",
                START,
                END,
                "80",
                "0.2000"
        );

        StrategyCandidateSimulation result = engine.simulate(
                context,
                candidate,
                baselineEngine.simulate(context),
                SimulationDetailLevel.SUMMARY_ONLY
        );

        assertThat(result.summary().expectedSalesQty()).isEqualByComparingTo("10");
        assertThat(result.summary().expectedRevenue()).isEqualByComparingTo("800");
        assertThat(result.summary().totalContributionMargin())
                .isEqualByComparingTo("150");
    }

    @Test
    void omitsDailySeriesInSummaryOnlyMode() {
        StrategyCalculationContext context = context(
                lot("10", null),
                forecasts("0", "0", "0"),
                forecasts("5", "5", "5"),
                price(10L, "100", "70"),
                price(20L, "110", "70"),
                true
        );

        StrategyCandidateSimulation result = engine.simulate(
                context,
                movementCandidate("10", START, null, StrategyType.REALLOCATION),
                baselineEngine.simulate(context),
                SimulationDetailLevel.SUMMARY_ONLY
        );

        assertThat(result.dailySeries()).isEmpty();
    }

    private static StrategyCandidate discountCandidate(
            String quantity,
            LocalDate start,
            LocalDate end,
            String strategyPrice,
            String discountRate
    ) {
        StrategyCandidate.Location source = new StrategyCandidate.Location(501L, 10L);
        StrategyCandidate.Action action = new StrategyCandidate.Action(
                StrategyType.PRICE_DISCOUNT,
                source,
                source,
                decimal(quantity),
                decimal("0"),
                decimal(strategyPrice),
                decimal(discountRate),
                List.of(allocation(quantity))
        );
        return new StrategyCandidate(
                "CAND-DISCOUNT",
                List.of(StrategyType.PRICE_DISCOUNT),
                start,
                end,
                List.of(action),
                List.of(CandidateAssumption.DISCOUNT_DEMAND_UPLIFT_NOT_APPLIED),
                preference(),
                new StrategyCandidate.DiscountEvidence(
                        decimal(quantity), decimal("10"), decimal("6"),
                        decimal(quantity), decimal("100"), decimal("70")
                )
        );
    }

    private static StrategyCandidate movementCandidate(
            String quantity,
            LocalDate start,
            LocalDate end,
            StrategyType type
    ) {
        StrategyCandidate.Action action = movementAction(quantity, type);
        return new StrategyCandidate(
                "CAND-MOVEMENT-" + start,
                List.of(type),
                start,
                end,
                List.of(action),
                List.of(),
                preference(),
                new StrategyCandidate.MovementEvidence(
                        decimal(quantity), decimal("10"), decimal("15"),
                        decimal(quantity)
                )
        );
    }

    private static StrategyCandidate channelExpansionCandidate(
            String quantity,
            LocalDate start,
            LocalDate end
    ) {
        StrategyCandidate.Action movement = movementAction(
                quantity,
                StrategyType.REALLOCATION
        );
        StrategyCandidate.Action channel = new StrategyCandidate.Action(
                StrategyType.CHANNEL_EXPANSION,
                movement.source(),
                movement.target(),
                decimal(quantity),
                decimal("0"),
                decimal("100"),
                null,
                List.of()
        );
        return new StrategyCandidate(
                "CAND-EXPANSION",
                List.of(StrategyType.CHANNEL_EXPANSION, StrategyType.REALLOCATION),
                start,
                end,
                List.of(movement, channel),
                List.of(CandidateAssumption.TARGET_COMMERCIAL_TERMS_COPIED_FROM_SOURCE),
                preference(),
                new StrategyCandidate.ChannelEvidence(
                        decimal(quantity), decimal("15"), decimal("0"),
                        decimal("15"), StrategyType.REALLOCATION,
                        decimal("100"), decimal("5"), decimal("10")
                )
        );
    }

    private static StrategyCandidate discountedChannelExpansionCandidate(
            String quantity,
            LocalDate start,
            LocalDate end,
            String strategyPrice,
            String discountRate
    ) {
        StrategyCandidate base = channelExpansionCandidate(quantity, start, end);
        StrategyCandidate.Action movement = base.actions().get(0);
        StrategyCandidate.Action discount = new StrategyCandidate.Action(
                StrategyType.PRICE_DISCOUNT,
                movement.target(),
                movement.target(),
                decimal(quantity),
                decimal("0"),
                decimal(strategyPrice),
                decimal(discountRate),
                movement.lotAllocations()
        );
        return new StrategyCandidate(
                "CAND-EXPANSION-DISCOUNT",
                List.of(
                        StrategyType.CHANNEL_EXPANSION,
                        StrategyType.REALLOCATION,
                        StrategyType.PRICE_DISCOUNT
                ),
                start,
                end,
                List.of(movement, base.actions().get(1), discount),
                List.of(
                        CandidateAssumption.TARGET_COMMERCIAL_TERMS_COPIED_FROM_SOURCE,
                        CandidateAssumption.DISCOUNT_DEMAND_UPLIFT_NOT_APPLIED
                ),
                preference(),
                base.evidence()
        );
    }

    private static StrategyCandidate.Action movementAction(
            String quantity,
            StrategyType type
    ) {
        return new StrategyCandidate.Action(
                type,
                new StrategyCandidate.Location(501L, 10L),
                new StrategyCandidate.Location(501L, 20L),
                decimal(quantity),
                decimal("0"),
                List.of(allocation(quantity))
        );
    }

    private static StrategyCandidate.LotAllocation allocation(String quantity) {
        return new StrategyCandidate.LotAllocation(
                1L,
                1001L,
                decimal(quantity),
                1
        );
    }

    private static StrategyCandidate.Preference preference() {
        return new StrategyCandidate.Preference(1, 1, 100);
    }

    private static StrategyCalculationContext context(
            StrategyCalculationContext.InventoryLot inventory,
            Map<LocalDate, BigDecimal> sourceForecast,
            Map<LocalDate, BigDecimal> targetForecast,
            StrategyCalculationContext.Price sourcePrice,
            StrategyCalculationContext.Price targetPrice,
            boolean targetListed
    ) {
        Map<Long, StrategyCalculationContext.SalesPoint> salesPoints =
                new LinkedHashMap<>();
        salesPoints.put(10L, salesPoint(
                10L,
                true,
                sourcePrice,
                sourceForecast
        ));
        salesPoints.put(20L, salesPoint(
                20L,
                targetListed,
                targetPrice,
                targetForecast
        ));
        return new StrategyCalculationContext(
                12345L,
                10L,
                LocalDateTime.of(2026, 8, 20, 10, 0),
                START,
                END,
                new StrategyCalculationContext.Sku(
                        101L, "SKU-101", "테스트 SKU", "EA", BigDecimal.ONE
                ),
                decimal("50"),
                new StrategyCalculationContext.RequestConstraints(
                        List.of(20L), List.of(), null, null
                ),
                List.of(inventory),
                List.of(inventory),
                List.of(),
                salesPoints,
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

    private static StrategyCalculationContext withTargetExistingInventory(
            StrategyCalculationContext original,
            StrategyCalculationContext.InventoryLot targetInventory
    ) {
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
                List.of(original.evaluationInventory().get(0), targetInventory),
                original.inventoryPolicies(),
                original.salesPoints(),
                original.forecastMetadata()
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
                original.forecastMetadata()
        );
    }

    private static StrategyCalculationContext.SalesPoint salesPoint(
            Long id,
            boolean listed,
            StrategyCalculationContext.Price price,
            Map<LocalDate, BigDecimal> forecast
    ) {
        return new StrategyCalculationContext.SalesPoint(
                id,
                "SP-" + id,
                "판매처 " + id,
                BigDecimal.ZERO,
                listed,
                price,
                forecast,
                List.of(new StrategyCalculationContext.WarehouseRoute(
                        id * 100,
                        id,
                        501L,
                        1,
                        null
                ))
        );
    }

    private static StrategyCalculationContext.Price price(
            Long salesPointId,
            String actualPrice,
            String minimumPrice
    ) {
        return new StrategyCalculationContext.Price(
                salesPointId * 10,
                decimal("120"),
                decimal(actualPrice),
                decimal(minimumPrice),
                decimal("5"),
                decimal("10")
        );
    }

    private static StrategyCalculationContext.InventoryLot lot(
            String quantity,
            LocalDate expiryDate
    ) {
        return new StrategyCalculationContext.InventoryLot(
                1L,
                1001L,
                501L,
                10L,
                10L,
                decimal(quantity),
                BigDecimal.ZERO,
                null,
                LocalDate.of(2026, 8, 1),
                expiryDate,
                null,
                "AVAILABLE"
        );
    }

    private static StrategyCalculationContext.InventoryLot targetLot(String quantity) {
        return new StrategyCalculationContext.InventoryLot(
                2L,
                2001L,
                501L,
                20L,
                20L,
                decimal(quantity),
                BigDecimal.ZERO,
                null,
                LocalDate.of(2026, 8, 1),
                null,
                null,
                "AVAILABLE"
        );
    }

    private static Map<LocalDate, BigDecimal> forecasts(String... quantities) {
        Map<LocalDate, BigDecimal> result = new LinkedHashMap<>();
        LocalDate date = START;
        for (String quantity : quantities) {
            result.put(date, decimal(quantity));
            date = date.plusDays(1);
        }
        return result;
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
