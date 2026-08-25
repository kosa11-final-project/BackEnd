package com.stockit.backend.feature.strategy.calculation.candidate.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.stockit.backend.feature.strategy.calculation.candidate.domain.MovementCandidatePlan;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;

class MovementLotAllocationPolicyTest {

    private static final LocalDate SALE_DATE = LocalDate.of(2026, 8, 20);

    private final MovementLotAllocationPolicy policy = new MovementLotAllocationPolicy();

    @Test
    void appliesFefoThenFifoForLotsWithoutExpiry() {
        StrategyCalculationContext.InventoryLot fifoOlder = lot(
                1L, 1001L, LocalDate.of(2026, 8, 1), null
        );
        StrategyCalculationContext.InventoryLot fifoNewer = lot(
                2L, 1002L, LocalDate.of(2026, 8, 2), null
        );
        StrategyCalculationContext.InventoryLot expiring = lot(
                3L, 1003L, LocalDate.of(2026, 8, 3), SALE_DATE
        );

        MovementCandidatePlan result = policy.plan(
                List.of(fifoNewer, fifoOlder, expiring),
                Map.of(
                        new StrategyCalculationContext.PhysicalLocation(501L, null),
                        decimal("30")
                ),
                new LinkedHashMap<>(Map.of(SALE_DATE, decimal("25"))),
                decimal("25")
        );

        assertThat(result.allocations())
                .extracting(MovementCandidatePlan.Allocation::lotId)
                .containsExactly(1003L, 1001L, 1002L);
        assertThat(result.allocations())
                .extracting(MovementCandidatePlan.Allocation::quantity)
                .containsExactly(decimal("10.000"), decimal("10.000"), decimal("5.000"));
    }

    @Test
    void doesNotSubtractReservedQuantityFromAlreadyUnreservedOnHandQuantity() {
        StrategyCalculationContext.InventoryLot lot = lot(
                1L,
                1001L,
                LocalDate.of(2026, 8, 1),
                null,
                "10",
                "8"
        );

        MovementCandidatePlan result = policy.plan(
                List.of(lot),
                Map.of(
                        new StrategyCalculationContext.PhysicalLocation(501L, null),
                        decimal("10")
                ),
                new LinkedHashMap<>(Map.of(SALE_DATE, decimal("10"))),
                decimal("10")
        );

        assertThat(result.plannedQuantity()).isEqualByComparingTo("10");
    }

    @Test
    void doesNotAllocateDemandOnOrAfterSaleStopDate() {
        StrategyCalculationContext.InventoryLot inventory = lot(
                1L,
                1001L,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 9, 1),
                SALE_DATE.plusDays(1)
        );
        Map<LocalDate, BigDecimal> demand = new LinkedHashMap<>();
        demand.put(SALE_DATE, decimal("3"));
        demand.put(SALE_DATE.plusDays(1), decimal("7"));

        MovementCandidatePlan result = policy.plan(
                List.of(inventory),
                Map.of(
                        new StrategyCalculationContext.PhysicalLocation(501L, null),
                        decimal("10")
                ),
                demand,
                decimal("10")
        );

        assertThat(result.plannedQuantity()).isEqualByComparingTo("3");
        assertThat(result.allocations()).singleElement().satisfies(allocation ->
                assertThat(allocation.quantity()).isEqualByComparingTo("3"));
    }

    private static StrategyCalculationContext.InventoryLot lot(
            Long balanceId,
            Long lotId,
            LocalDate receivedDate,
            LocalDate expiryDate
    ) {
        return lot(balanceId, lotId, receivedDate, expiryDate, "10", "0", null);
    }

    private static StrategyCalculationContext.InventoryLot lot(
            Long balanceId,
            Long lotId,
            LocalDate receivedDate,
            LocalDate expiryDate,
            String onHandQuantity,
            String reservedQuantity
    ) {
        return lot(
                balanceId,
                lotId,
                receivedDate,
                expiryDate,
                onHandQuantity,
                reservedQuantity,
                null
        );
    }

    private static StrategyCalculationContext.InventoryLot lot(
            Long balanceId,
            Long lotId,
            LocalDate receivedDate,
            LocalDate expiryDate,
            LocalDate saleStopDate
    ) {
        return lot(
                balanceId,
                lotId,
                receivedDate,
                expiryDate,
                "10",
                "0",
                saleStopDate
        );
    }

    private static StrategyCalculationContext.InventoryLot lot(
            Long balanceId,
            Long lotId,
            LocalDate receivedDate,
            LocalDate expiryDate,
            String onHandQuantity,
            String reservedQuantity,
            LocalDate saleStopDate
    ) {
        return new StrategyCalculationContext.InventoryLot(
                balanceId,
                lotId,
                501L,
                10L,
                10L,
                decimal(onHandQuantity),
                decimal(reservedQuantity),
                null,
                receivedDate,
                expiryDate,
                saleStopDate,
                "AVAILABLE"
        );
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
