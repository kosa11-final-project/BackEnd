package com.stockit.backend.feature.strategy.calculation.candidate.calculator;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.stockit.backend.feature.strategy.calculation.candidate.policy.MovementLotAllocationPolicy;
import com.stockit.backend.feature.strategy.calculation.candidate.policy.SafetyStockPolicyResolver;
import com.stockit.backend.feature.strategy.calculation.candidate.policy.SourceInventoryCapacityPolicy;
import com.stockit.backend.feature.strategy.calculation.candidate.policy.TargetAdditionalDemandPolicy;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.domain.StrategyType;

class InventoryMovementCandidateFactoryTest {

    private static final LocalDate START = LocalDate.of(2026, 8, 20);
    private static final LocalDate END = LocalDate.of(2026, 8, 29);

    private InventoryMovementCandidateFactory factory;

    @BeforeEach
    void setUp() {
        factory = new InventoryMovementCandidateFactory(
                new SourceInventoryCapacityPolicy(new SafetyStockPolicyResolver()),
                new TargetAdditionalDemandPolicy(),
                new MovementLotAllocationPolicy(),
                new StrategyCandidateIdGenerator(),
                new InventoryTransferCostCalculator()
        );
    }

    @Test
    void generatesTenPercentReallocationTiersWithinSafetyStockAndSharedWarehouse() {
        StrategyCalculationContext context = context(
                List.of(lot(1L, 1001L, 501L, 10L, "100", null)),
                List.of(policy(1L, 501L, 10L, "20")),
                List.of(route(20L, 501L, 1)),
                forecasts("10"),
                StrategyType.REALLOCATION
        );

        CandidateGenerationResult result = factory.generate(
                context,
                StrategyType.REALLOCATION,
                1
        );

        assertThat(result.exclusions()).isEmpty();
        assertThat(result.candidates()).hasSize(10);
        assertThat(result.candidates())
                .extracting(candidate -> candidate.actions().get(0).actionQuantity())
                .containsExactly(
                        decimal("8.000"), decimal("16.000"), decimal("24.000"),
                        decimal("32.000"), decimal("40.000"), decimal("48.000"),
                        decimal("56.000"), decimal("64.000"), decimal("72.000"),
                        decimal("80.000")
                );
        StrategyCandidate maximum = result.candidates().get(9);
        assertThat(maximum.startDate()).isEqualTo(START);
        assertThat(maximum.endDate()).isNull();
        assertThat(maximum.evidence().maxExecutableQty()).isEqualByComparingTo("80");
        assertThat(maximum.actions()).singleElement().satisfies(action -> {
            assertThat(action.source().warehouseId()).isEqualTo(501L);
            assertThat(action.source().salesPointId()).isEqualTo(10L);
            assertThat(action.target().warehouseId()).isEqualTo(501L);
            assertThat(action.target().salesPointId()).isEqualTo(20L);
            assertThat(action.estimatedActionCost()).isEqualByComparingTo("0");
        });
        assertThat(maximum.assumptions()).isEmpty();
    }

    @Test
    void roundsTenPercentCandidateTiersDownToWholeUnits() {
        StrategyCalculationContext context = context(
                List.of(lot(1L, 1001L, 501L, 10L, "15", null)),
                List.of(policy(1L, 501L, 10L, "0")),
                List.of(route(20L, 501L, 1)),
                forecasts("10"),
                StrategyType.REALLOCATION
        );

        CandidateGenerationResult result = factory.generate(
                context,
                StrategyType.REALLOCATION,
                1
        );

        assertThat(result.candidates())
                .extracting(candidate -> candidate.actions().get(0).actionQuantity())
                .containsExactly(
                        decimal("1.000"), decimal("3.000"), decimal("4.000"),
                        decimal("6.000"), decimal("7.000"), decimal("9.000"),
                        decimal("10.000"), decimal("12.000"), decimal("13.000"),
                        decimal("15.000")
                );
        assertThat(result.candidates()).allSatisfy(candidate ->
                assertThat(candidate.evidence().maxExecutableQty())
                        .isEqualByComparingTo("15"));
    }

    @Test
    void generatesPhysicalTransferWithCalculatedMovementCost() {
        StrategyCalculationContext context = context(
                List.of(lot(1L, 1001L, 501L, 10L, "100", null)),
                List.of(),
                List.of(route(20L, 503L, 2), route(20L, 502L, 1)),
                forecasts("10"),
                StrategyType.RT_TRANSFER
        );

        CandidateGenerationResult result = factory.generate(
                context,
                StrategyType.RT_TRANSFER,
                1
        );

        assertThat(result.candidates()).hasSize(10);
        StrategyCandidate maximum = result.candidates().get(9);
        assertThat(maximum.endDate()).isNull();
        assertThat(maximum.actions()).singleElement().satisfies(action -> {
            assertThat(action.target().warehouseId()).isEqualTo(502L);
            assertThat(action.estimatedActionCost()).isEqualByComparingTo("10000");
            assertThat(action.movementCost()).isNotNull();
            assertThat(action.movementCost().distanceKm()).isEqualByComparingTo("100");
        });
        assertThat(maximum.assumptions()).containsExactly(
                CandidateAssumption.SAFETY_STOCK_DEFAULTED_TO_ZERO
        );
    }

    @Test
    void generatesDirectSalesPointToSalesPointTransferWithoutWarehouseRoute() {
        StrategyCalculationContext original = context(
                List.of(lot(1L, 1001L, null, 10L, "100", null)),
                List.of(policy(1L, null, 10L, "0")),
                List.of(),
                forecasts("10"),
                StrategyType.RT_TRANSFER
        );
        StrategyCalculationContext context = withTransferRoutes(
                original,
                List.of(new StrategyCalculationContext.TransferRoute(
                        9002L,
                        new StrategyCalculationContext.PhysicalLocation(null, 10L),
                        new StrategyCalculationContext.PhysicalLocation(null, 20L),
                        new BigDecimal("10"),
                        "DUMMY",
                        null,
                        null
                ))
        );

        CandidateGenerationResult result = factory.generate(
                context,
                StrategyType.RT_TRANSFER,
                1
        );

        assertThat(result.candidates()).hasSize(10);
        assertThat(result.candidates().get(9).actions()).singleElement()
                .satisfies(action -> {
                    assertThat(action.source().warehouseId()).isNull();
                    assertThat(action.source().salesPointId()).isEqualTo(10L);
                    assertThat(action.target().warehouseId()).isNull();
                    assertThat(action.target().salesPointId()).isEqualTo(20L);
                    assertThat(action.estimatedActionCost())
                            .isEqualByComparingTo("1000");
                });
    }

    @Test
    void limitsPhysicalTransferDestinationsPerTargetToThree() {
        StrategyCalculationContext original = context(
                List.of(lot(1L, 1001L, 501L, 10L, "100", null)),
                List.of(),
                List.of(
                        route(20L, 502L, 1),
                        route(20L, 503L, 2),
                        route(20L, 504L, 3),
                        route(20L, 505L, 4)
                ),
                forecasts("10"),
                StrategyType.RT_TRANSFER
        );
        StrategyCalculationContext context = withTransferRoutes(
                original,
                List.of(
                        transferRoute(9001L, 501L, null, null, 20L),
                        transferRoute(9002L, 501L, null, 502L, null),
                        transferRoute(9003L, 501L, null, 503L, null),
                        transferRoute(9004L, 501L, null, 504L, null),
                        transferRoute(9005L, 501L, null, 505L, null)
                )
        );

        CandidateGenerationResult result = factory.generate(
                context,
                StrategyType.RT_TRANSFER,
                1
        );

        assertThat(result.candidates()).hasSize(30);
        assertThat(result.candidates().stream()
                .map(candidate -> candidate.actions().get(0).target())
                .distinct())
                .containsExactlyInAnyOrder(
                        new StrategyCandidate.Location(null, 20L),
                        new StrategyCandidate.Location(502L, 20L),
                        new StrategyCandidate.Location(503L, 20L)
                );
    }

    @Test
    void preservesSelectedLaterLotUntilEarlierReferenceLotIsConsumed() {
        StrategyCalculationContext.InventoryLot earlier = lot(
                1L, 1001L, 501L, 10L, "10", null
        );
        StrategyCalculationContext.InventoryLot selected = lot(
                2L, 1002L, 501L, 10L, "10", null
        );
        StrategyCalculationContext context = context(
                List.of(selected),
                List.of(earlier, selected),
                List.of(policy(1L, 501L, 10L, "0")),
                List.of(route(20L, 501L, 1)),
                forecasts("10"),
                StrategyType.REALLOCATION,
                START.plusDays(1),
                END
        );

        CandidateGenerationResult result = factory.generate(
                context,
                StrategyType.REALLOCATION,
                1
        );

        assertThat(result.candidates()).isNotEmpty();
        assertThat(result.candidates().get(result.candidates().size() - 1)
                .evidence().maxExecutableQty()).isEqualByComparingTo("10");
    }

    @Test
    void capsMaximumQuantityByLotExpiryAgainstDailyDemand() {
        StrategyCalculationContext context = context(
                List.of(lot(1L, 1001L, 501L, 10L, "100", START)),
                List.of(policy(1L, 501L, 10L, "0")),
                List.of(route(20L, 501L, 1)),
                forecasts("10"),
                StrategyType.REALLOCATION
        );

        CandidateGenerationResult result = factory.generate(
                context,
                StrategyType.REALLOCATION,
                1
        );

        assertThat(result.candidates()).hasSize(10);
        assertThat(result.candidates().get(9).evidence().maxExecutableQty())
                .isEqualByComparingTo("10");
    }

    @Test
    void excludesReallocationWhenTargetDoesNotShareSourceWarehouse() {
        StrategyCalculationContext context = context(
                List.of(lot(1L, 1001L, 501L, 10L, "100", null)),
                List.of(),
                List.of(route(20L, 502L, 1)),
                forecasts("10"),
                StrategyType.REALLOCATION
        );

        CandidateGenerationResult result = factory.generate(
                context,
                StrategyType.REALLOCATION,
                1
        );

        assertThat(result.candidates()).isEmpty();
        assertThat(result.exclusions()).singleElement().satisfies(exclusion ->
                assertThat(exclusion.reason()).isEqualTo(
                        CandidateExclusionReason.SHARED_WAREHOUSE_NOT_FOUND
                ));
    }

    @Test
    void subtractsTargetExistingInventoryBeforeCreatingMovementCapacity() {
        StrategyCalculationContext.InventoryLot source = lot(
                1L, 1001L, 501L, 10L, "100", null
        );
        StrategyCalculationContext.InventoryLot targetExisting = lot(
                2L, 2001L, 501L, 20L, "30", "25", null
        );
        StrategyCalculationContext context = context(
                List.of(source),
                List.of(source, targetExisting),
                List.of(policy(1L, 501L, 10L, "0")),
                List.of(route(20L, 501L, 1)),
                forecasts("5"),
                StrategyType.REALLOCATION
        );

        CandidateGenerationResult result = factory.generate(
                context,
                StrategyType.REALLOCATION,
                1
        );

        StrategyCandidate.MovementEvidence evidence =
                (StrategyCandidate.MovementEvidence) result.candidates()
                        .get(9).evidence();
        assertThat(evidence.targetAdditionalDemandQty())
                .isEqualByComparingTo("20");
        assertThat(result.candidates().get(9).actions().get(0).actionQuantity())
                .isEqualByComparingTo("20");
    }

    @Test
    void appliesUserFixedSingleDayToDemandAndKeepsStandaloneEndDateOpen() {
        LocalDate fixedDate = START.plusDays(4);
        StrategyCalculationContext context = context(
                List.of(lot(1L, 1001L, 501L, 10L, "100", null)),
                List.of(policy(1L, 501L, 10L, "0")),
                List.of(route(20L, 501L, 1)),
                forecasts("10"),
                StrategyType.REALLOCATION,
                fixedDate,
                fixedDate
        );

        CandidateGenerationResult result = factory.generate(
                context,
                StrategyType.REALLOCATION,
                1
        );

        StrategyCandidate maximum = result.candidates().get(9);
        StrategyCandidate.MovementEvidence evidence =
                (StrategyCandidate.MovementEvidence) maximum.evidence();
        assertThat(maximum.startDate()).isEqualTo(fixedDate);
        assertThat(maximum.endDate()).isNull();
        assertThat(evidence.targetAdditionalDemandQty())
                .isEqualByComparingTo("10");
        assertThat(evidence.maxExecutableQty())
                .isEqualByComparingTo("10");
    }

    @Test
    void limitsFutureStrategyQuantityToProjectedRemainingInventory() {
        LocalDate fixedDate = START.plusDays(4);
        StrategyCalculationContext context = context(
                List.of(lot(1L, 1001L, 501L, 10L, "100", null)),
                List.of(policy(1L, 501L, 10L, "0")),
                List.of(route(20L, 501L, 1)),
                forecasts("100"),
                StrategyType.REALLOCATION,
                fixedDate,
                fixedDate
        );

        CandidateGenerationResult result = factory.generate(
                context,
                StrategyType.REALLOCATION,
                1
        );

        StrategyCandidate maximum = result.candidates().get(9);
        assertThat(maximum.evidence().maxExecutableQty())
                .isEqualByComparingTo("88");
        assertThat(maximum.actions().get(0).actionQuantity())
                .isEqualByComparingTo("88");
    }

    private static StrategyCalculationContext context(
            List<StrategyCalculationContext.InventoryLot> inventory,
            List<StrategyCalculationContext.InventoryPolicy> policies,
            List<StrategyCalculationContext.WarehouseRoute> targetRoutes,
            Map<LocalDate, BigDecimal> targetForecast,
            StrategyType requestedType
    ) {
        return context(
                inventory,
                policies,
                targetRoutes,
                targetForecast,
                requestedType,
                null,
                null
        );
    }

    private static StrategyCalculationContext context(
            List<StrategyCalculationContext.InventoryLot> inventory,
            List<StrategyCalculationContext.InventoryPolicy> policies,
            List<StrategyCalculationContext.WarehouseRoute> targetRoutes,
            Map<LocalDate, BigDecimal> targetForecast,
            StrategyType requestedType,
            LocalDate preferredStartDate,
            LocalDate preferredEndDate
    ) {
        return context(inventory, inventory, policies, targetRoutes, targetForecast,
                requestedType, preferredStartDate, preferredEndDate);
    }

    private static StrategyCalculationContext context(
            List<StrategyCalculationContext.InventoryLot> evaluationInventory,
            List<StrategyCalculationContext.InventoryLot> referenceInventory,
            List<StrategyCalculationContext.InventoryPolicy> policies,
            List<StrategyCalculationContext.WarehouseRoute> targetRoutes,
            Map<LocalDate, BigDecimal> targetForecast,
            StrategyType requestedType
    ) {
        return context(
                evaluationInventory,
                referenceInventory,
                policies,
                targetRoutes,
                targetForecast,
                requestedType,
                null,
                null
        );
    }

    private static StrategyCalculationContext context(
            List<StrategyCalculationContext.InventoryLot> evaluationInventory,
            List<StrategyCalculationContext.InventoryLot> referenceInventory,
            List<StrategyCalculationContext.InventoryPolicy> policies,
            List<StrategyCalculationContext.WarehouseRoute> targetRoutes,
            Map<LocalDate, BigDecimal> targetForecast,
            StrategyType requestedType,
            LocalDate preferredStartDate,
            LocalDate preferredEndDate
    ) {
        Map<Long, StrategyCalculationContext.SalesPoint> salesPoints = new LinkedHashMap<>();
        salesPoints.put(10L, salesPoint(10L, forecasts("3"), List.of()));
        salesPoints.put(20L, salesPoint(20L, targetForecast, targetRoutes));
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
                        List.of(requestedType),
                        preferredStartDate,
                        preferredEndDate
                ),
                evaluationInventory,
                referenceInventory,
                policies,
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
            Map<LocalDate, BigDecimal> forecast,
            List<StrategyCalculationContext.WarehouseRoute> routes
    ) {
        return new StrategyCalculationContext.SalesPoint(
                id,
                "SP-" + id,
                "판매처 " + id,
                BigDecimal.ZERO,
                new StrategyCalculationContext.Price(
                        id * 10,
                        decimal("120"),
                        decimal("100"),
                        decimal("70"),
                        decimal("5"),
                        decimal("10")
                ),
                forecast,
                routes
        );
    }

    private static StrategyCalculationContext withTransferRoutes(
            StrategyCalculationContext original,
            List<StrategyCalculationContext.TransferRoute> routes
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
                original.referenceInventory(),
                original.inventoryPolicies(),
                original.salesPoints(),
                original.forecastMetadata(),
                routes,
                original.transferCostPolicies()
        );
    }

    private static StrategyCalculationContext.InventoryLot lot(
            Long balanceId,
            Long lotId,
            Long warehouseId,
            Long salesPointId,
            String quantity,
            LocalDate expiryDate
    ) {
        return lot(
                balanceId,
                lotId,
                warehouseId,
                salesPointId,
                quantity,
                "0",
                expiryDate
        );
    }

    private static StrategyCalculationContext.InventoryLot lot(
            Long balanceId,
            Long lotId,
            Long warehouseId,
            Long salesPointId,
            String quantity,
            String reservedQuantity,
            LocalDate expiryDate
    ) {
        return new StrategyCalculationContext.InventoryLot(
                balanceId,
                lotId,
                warehouseId,
                salesPointId,
                salesPointId,
                decimal(quantity),
                decimal(reservedQuantity),
                null,
                LocalDate.of(2026, 8, 1),
                expiryDate,
                null,
                "AVAILABLE"
        );
    }

    private static StrategyCalculationContext.InventoryPolicy policy(
            Long id,
            Long warehouseId,
            Long salesPointId,
            String safetyStock
    ) {
        return new StrategyCalculationContext.InventoryPolicy(
                id,
                warehouseId,
                salesPointId,
                salesPointId,
                decimal(safetyStock),
                null,
                null,
                null
        );
    }

    private static StrategyCalculationContext.WarehouseRoute route(
            Long salesPointId,
            Long warehouseId,
            int priority
    ) {
        return new StrategyCalculationContext.WarehouseRoute(
                salesPointId * 1000 + warehouseId,
                salesPointId,
                warehouseId,
                priority,
                null
        );
    }

    private static StrategyCalculationContext.TransferRoute transferRoute(
            Long routeId,
            Long sourceWarehouseId,
            Long sourceSalesPointId,
            Long destinationWarehouseId,
            Long destinationSalesPointId
    ) {
        return new StrategyCalculationContext.TransferRoute(
                routeId,
                new StrategyCalculationContext.PhysicalLocation(
                        sourceWarehouseId,
                        sourceSalesPointId
                ),
                new StrategyCalculationContext.PhysicalLocation(
                        destinationWarehouseId,
                        destinationSalesPointId
                ),
                new BigDecimal("10"),
                "DUMMY",
                null,
                null
        );
    }

    private static Map<LocalDate, BigDecimal> forecasts(String dailyQuantity) {
        Map<LocalDate, BigDecimal> forecasts = new LinkedHashMap<>();
        for (LocalDate date = START; !date.isAfter(END); date = date.plusDays(1)) {
            forecasts.put(date, decimal(dailyQuantity));
        }
        return forecasts;
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
