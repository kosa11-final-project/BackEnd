package com.stockit.backend.feature.strategy.calculation.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.stockit.backend.feature.strategy.calculation.domain.BaselineSimulation;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;

class BaselineSimulationEngineTest {

    private final BaselineSimulationEngine engine = new BaselineSimulationEngine();

    @Test
    void calculatesFefoSalesDisposalRevenueAndContributionMargin() {
        StrategyCalculationContext context = context(
                10L,
                LocalDate.of(2026, 8, 20),
                LocalDate.of(2026, 8, 22),
                List.of(
                        lot(1L, 1001L, "5", LocalDate.of(2026, 8, 1),
                                LocalDate.of(2026, 8, 20), null, "AVAILABLE"),
                        lot(2L, 1002L, "10", LocalDate.of(2026, 8, 2),
                                null, null, "AVAILABLE")
                ),
                forecasts("3", "4", "5")
        );

        BaselineSimulation result = engine.simulate(context);

        assertThat(result.summary().expectedSalesQty()).isEqualByComparingTo("12.000");
        assertThat(result.summary().expectedDisposalQty()).isEqualByComparingTo("2.000");
        assertThat(result.summary().expectedRemainingQty()).isEqualByComparingTo("1.000");
        assertThat(result.summary().expectedRevenue()).isEqualByComparingTo("1200.00");
        assertThat(result.summary().totalContributionMargin())
                .isEqualByComparingTo("420.00");
        assertThat(result.summary().contributionMarginRate())
                .isEqualByComparingTo("0.3500");
        assertThat(result.summary().expectedSellThroughDays()).isNull();
        assertThat(result.dailySeries()).extracting(BaselineSimulation.DailyPoint::expectedSalesQty)
                .containsExactly(decimal("3.000"), decimal("4.000"), decimal("5.000"));
        assertThat(result.dailySeries()).extracting(BaselineSimulation.DailyPoint::expectedRemainingQty)
                .containsExactly(decimal("12.000"), decimal("6.000"), decimal("1.000"));
    }

    @Test
    void stopsSellingOnSaleStopDateWithoutCountingItAsDisposal() {
        StrategyCalculationContext context = context(
                10L,
                LocalDate.of(2026, 8, 20),
                LocalDate.of(2026, 8, 22),
                List.of(lot(
                        1L,
                        1001L,
                        "10",
                        LocalDate.of(2026, 8, 1),
                        null,
                        LocalDate.of(2026, 8, 21),
                        "AVAILABLE"
                )),
                forecasts("3", "3", "3")
        );

        BaselineSimulation result = engine.simulate(context);

        assertThat(result.summary().expectedSalesQty()).isEqualByComparingTo("3.000");
        assertThat(result.summary().expectedRemainingQty()).isEqualByComparingTo("7.000");
        assertThat(result.summary().expectedDisposalQty()).isEqualByComparingTo("0.000");
        assertThat(result.summary().expectedSellThroughDays()).isNull();
        assertThat(result.dailySeries()).extracting(BaselineSimulation.DailyPoint::expectedSalesQty)
                .containsExactly(decimal("3.000"), decimal("0.000"), decimal("0.000"));
    }

    @Test
    void consumesNonExpiringLotsByReceivedDateFifo() {
        StrategyCalculationContext context = context(
                10L,
                LocalDate.of(2026, 8, 20),
                LocalDate.of(2026, 8, 21),
                List.of(
                        lot(1L, 1001L, "5", LocalDate.of(2026, 7, 1), null,
                                LocalDate.of(2026, 8, 21), "AVAILABLE"),
                        lot(2L, 1002L, "5", LocalDate.of(2026, 8, 1), null,
                                null, "AVAILABLE")
                ),
                forecasts("5", "5")
        );

        BaselineSimulation result = engine.simulate(context);

        assertThat(result.summary().expectedSalesQty()).isEqualByComparingTo("10.000");
        assertThat(result.summary().expectedRemainingQty()).isEqualByComparingTo("0.000");
        assertThat(result.summary().expectedSellThroughDays()).isEqualTo(2);
    }

    @Test
    void doesNotReportDisposalAsSellThrough() {
        StrategyCalculationContext context = context(
                null,
                LocalDate.of(2026, 8, 20),
                LocalDate.of(2026, 8, 21),
                List.of(lot(
                        1L,
                        1001L,
                        "5",
                        LocalDate.of(2026, 8, 1),
                        LocalDate.of(2026, 8, 20),
                        null,
                        "AVAILABLE"
                )),
                Map.of()
        );

        BaselineSimulation result = engine.simulate(context);

        assertThat(result.summary().expectedSalesQty()).isEqualByComparingTo("0.000");
        assertThat(result.summary().expectedDisposalQty()).isEqualByComparingTo("5.000");
        assertThat(result.summary().expectedRemainingQty()).isEqualByComparingTo("0.000");
        assertThat(result.summary().expectedSellThroughDays()).isNull();
        assertThat(result.summary().contributionMarginRate())
                .isEqualByComparingTo("0.0000");
    }

    @Test
    void usesOnHandQuantityWithoutSubtractingReservedQuantityAgain() {
        StrategyCalculationContext.InventoryLot inventory = new StrategyCalculationContext.InventoryLot(
                1L,
                1001L,
                501L,
                10L,
                10L,
                decimal("10"),
                decimal("9"),
                null,
                LocalDate.of(2026, 8, 1),
                null,
                null,
                "AVAILABLE"
        );
        StrategyCalculationContext context = context(
                10L,
                LocalDate.of(2026, 8, 20),
                LocalDate.of(2026, 8, 20),
                List.of(inventory),
                forecasts("10")
        );

        BaselineSimulation result = engine.simulate(context);

        assertThat(result.summary().expectedSalesQty()).isEqualByComparingTo("10.000");
        assertThat(result.summary().expectedRemainingQty()).isEqualByComparingTo("0.000");
    }

    @Test
    void roundsFractionalDemandDownToInventoryQuantityPrecision() {
        StrategyCalculationContext context = context(
                10L,
                LocalDate.of(2026, 8, 20),
                LocalDate.of(2026, 8, 20),
                List.of(lot(1L, 1001L, "1", LocalDate.of(2026, 8, 1),
                        null, null, "AVAILABLE")),
                forecasts("0.1239")
        );

        BaselineSimulation result = engine.simulate(context);

        assertThat(result.summary().expectedSalesQty()).isEqualByComparingTo("0.123");
        assertThat(result.summary().expectedRemainingQty()).isEqualByComparingTo("0.877");
    }

    private static StrategyCalculationContext context(
            Long sourceSalesPointId,
            LocalDate startDate,
            LocalDate endDate,
            List<StrategyCalculationContext.InventoryLot> inventory,
            Map<LocalDate, BigDecimal> forecasts
    ) {
        Map<Long, StrategyCalculationContext.SalesPoint> salesPoints = new LinkedHashMap<>();
        if (sourceSalesPointId != null) {
            salesPoints.put(sourceSalesPointId, new StrategyCalculationContext.SalesPoint(
                    sourceSalesPointId,
                    "SP-" + sourceSalesPointId,
                    "판매처 " + sourceSalesPointId,
                    decimal("20"),
                    new StrategyCalculationContext.Price(
                            100L,
                            decimal("120"),
                            decimal("100"),
                            decimal("70"),
                            decimal("5"),
                            decimal("10")
                    ),
                    forecasts,
                    List.of()
            ));
        }
        return new StrategyCalculationContext(
                12345L,
                sourceSalesPointId,
                LocalDateTime.of(2026, 8, 20, 10, 0),
                startDate,
                endDate,
                new StrategyCalculationContext.Sku(
                        101L,
                        "SKU-101",
                        "테스트 SKU",
                        "EA",
                        BigDecimal.ONE
                ),
                decimal("50"),
                new StrategyCalculationContext.RequestConstraints(
                        List.of(),
                        List.of(),
                        null,
                        null
                ),
                inventory,
                inventory,
                List.of(),
                salesPoints,
                new StrategyCalculationContext.ForecastMetadata(
                        "forecast-run-1",
                        3L,
                        OffsetDateTime.of(2026, 8, 20, 9, 0, 0, 0, ZoneOffset.ofHours(9))
                )
        );
    }

    private static StrategyCalculationContext.InventoryLot lot(
            Long inventoryBalanceId,
            Long lotId,
            String quantity,
            LocalDate receivedDate,
            LocalDate expiryDate,
            LocalDate saleStopDate,
            String status
    ) {
        return new StrategyCalculationContext.InventoryLot(
                inventoryBalanceId,
                lotId,
                501L,
                10L,
                10L,
                decimal(quantity),
                BigDecimal.ZERO,
                null,
                receivedDate,
                expiryDate,
                saleStopDate,
                status
        );
    }

    private static Map<LocalDate, BigDecimal> forecasts(String... quantities) {
        Map<LocalDate, BigDecimal> result = new LinkedHashMap<>();
        LocalDate date = LocalDate.of(2026, 8, 20);
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
