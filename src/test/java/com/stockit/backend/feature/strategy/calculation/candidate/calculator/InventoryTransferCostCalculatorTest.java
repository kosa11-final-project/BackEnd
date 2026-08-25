package com.stockit.backend.feature.strategy.calculation.candidate.calculator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.stockit.backend.feature.strategy.calculation.candidate.domain.CandidateExclusionReason;
import com.stockit.backend.feature.strategy.calculation.candidate.domain.StrategyCandidate;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;

class InventoryTransferCostCalculatorTest {

    private static final LocalDate START = LocalDate.of(2026, 8, 20);

    private final InventoryTransferCostCalculator calculator =
            new InventoryTransferCostCalculator();

    @Test
    void calculatesAllWarehouseAndSalesPointDirectionCombinations() {
        StrategyCalculationContext context = context(List.of(
                route(1L, warehouse(501L), warehouse(502L)),
                route(2L, warehouse(501L), salesPoint(20L)),
                route(3L, salesPoint(10L), warehouse(502L)),
                route(4L, salesPoint(10L), salesPoint(20L))
        ));
        List<RouteCase> cases = List.of(
                new RouteCase(1L, location(501L, null), location(502L, null)),
                new RouteCase(2L, location(501L, null), location(null, 20L)),
                new RouteCase(3L, location(null, 10L), location(502L, null)),
                new RouteCase(4L, location(null, 10L), location(null, 20L))
        );

        for (RouteCase value : cases) {
            StrategyCandidate.MovementCost result = calculator.calculate(
                    context,
                    value.source(),
                    value.target(),
                    new BigDecimal("10"),
                    START
            );
            assertThat(result.transferRouteId()).isEqualTo(value.routeId());
            assertThat(result.weightKg()).isEqualByComparingTo("5");
            assertThat(result.estimatedCost()).isEqualByComparingTo("1000");
        }
    }

    @Test
    void rejectsTransferWhenDirectionalRouteIsMissing() {
        StrategyCalculationContext context = context(List.of(
                route(1L, warehouse(501L), salesPoint(20L))
        ));

        assertThatThrownBy(() -> calculator.calculate(
                context,
                location(null, 20L),
                location(501L, null),
                BigDecimal.ONE,
                START
        )).isInstanceOfSatisfying(
                InventoryTransferCostCalculationException.class,
                exception -> assertThat(exception.getReason()).isEqualTo(
                        CandidateExclusionReason.TRANSFER_ROUTE_NOT_FOUND
                )
        );
    }

    private static StrategyCalculationContext context(
            List<StrategyCalculationContext.TransferRoute> routes
    ) {
        StrategyCalculationContext.InventoryLot inventory =
                new StrategyCalculationContext.InventoryLot(
                        1L, 1001L, 501L, 10L, 10L,
                        BigDecimal.TEN, BigDecimal.ZERO,
                        null, START.minusDays(1), null, null, "AVAILABLE"
                );
        return new StrategyCalculationContext(
                1L, 10L, START.atStartOfDay(), START, START.plusDays(1),
                new StrategyCalculationContext.Sku(
                        101L, "SKU-101", "테스트", "EA", BigDecimal.ONE,
                        new BigDecimal("500"), "G"
                ),
                BigDecimal.ONE,
                new StrategyCalculationContext.RequestConstraints(
                        List.of(), List.of(), null, null
                ),
                List.of(inventory), List.of(inventory), List.of(), Map.of(),
                new StrategyCalculationContext.ForecastMetadata(
                        "run-1", 1L,
                        OffsetDateTime.of(
                                LocalDateTime.of(2026, 8, 20, 0, 0),
                                ZoneOffset.ofHours(9)
                        )
                ),
                routes,
                List.of(new StrategyCalculationContext.TransferCostPolicy(
                        1L, "COMMON", new BigDecimal("2"), START, null
                ))
        );
    }

    private static StrategyCalculationContext.TransferRoute route(
            Long id,
            StrategyCalculationContext.PhysicalLocation source,
            StrategyCalculationContext.PhysicalLocation target
    ) {
        return new StrategyCalculationContext.TransferRoute(
                id, source, target, new BigDecimal("100"),
                "DUMMY", null, null
        );
    }

    private static StrategyCalculationContext.PhysicalLocation warehouse(Long id) {
        return new StrategyCalculationContext.PhysicalLocation(id, null);
    }

    private static StrategyCalculationContext.PhysicalLocation salesPoint(Long id) {
        return new StrategyCalculationContext.PhysicalLocation(null, id);
    }

    private static StrategyCandidate.Location location(
            Long warehouseId,
            Long salesPointId
    ) {
        return new StrategyCandidate.Location(warehouseId, salesPointId);
    }

    private record RouteCase(
            Long routeId,
            StrategyCandidate.Location source,
            StrategyCandidate.Location target
    ) {
    }
}
