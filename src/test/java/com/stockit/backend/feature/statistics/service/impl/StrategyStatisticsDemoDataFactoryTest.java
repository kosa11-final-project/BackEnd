package com.stockit.backend.feature.statistics.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

class StrategyStatisticsDemoDataFactoryTest {
    private final StrategyStatisticsDemoDataFactory factory = new StrategyStatisticsDemoDataFactory();
    private final StrategyStatisticsDemoDimensions dimensions = dimensions();

    @Test
    void createsExactly360DeterministicStrategiesFromActualSales() {
        LocalDate from = LocalDate.of(2025, 8, 24);
        LocalDate to = LocalDate.of(2026, 8, 23);

        List<StrategyStatisticsDemoData> first = factory.create(from, to, dimensions);
        List<StrategyStatisticsDemoData> second = factory.create(from, to, dimensions);

        assertThat(first).isEqualTo(second).hasSize(360);
        assertThat(first).allSatisfy(value -> {
            assertThat(value.endDate()).isBetween(from, to);
            assertThat(value.startDate()).isEqualTo(value.endDate().minusDays(6));
            assertThat(value.goalActualValue()).isPositive();
            assertThat(value.endRiskStockQty()).isLessThan(value.startRiskStockQty());
            assertThat(value.startRiskStockQty().subtract(value.endRiskStockQty()))
                    .isEqualByComparingTo(value.goalActualValue().min(value.startRiskStockQty()));
            assertThat(value.endExpectedDisposalQty()).isLessThan(value.startExpectedDisposalQty());
            assertThat(value.achievementRate()).isEqualByComparingTo(
                    value.goalActualValue().multiply(BigDecimal.valueOf(100))
                            .divide(value.goalTargetValue(), 6, java.math.RoundingMode.HALF_UP));
            assertThat(value.performance()).hasSize(7);
            assertThat(value.performance().stream()
                    .map(StrategyStatisticsDemoPerformance::actualSalesQty)
                    .reduce(BigDecimal.ZERO, BigDecimal::add))
                    .isEqualByComparingTo(value.goalActualValue());
        });
        assertThat(first.stream().map(StrategyStatisticsDemoData::caseId)).doesNotHaveDuplicates();
    }

    @Test
    void enforcesMovementSemanticsAndBalancedSnapshots() {
        List<StrategyStatisticsDemoData> values = factory.create(
                LocalDate.of(2025, 8, 24), LocalDate.of(2026, 8, 23), dimensions);

        assertThat(values.stream().flatMap(value -> value.actions().stream())
                .map(StrategyStatisticsDemoAction::actionType))
                .contains("PRICE_DISCOUNT", "REALLOCATION", "CHANNEL_EXPANSION", "RT_TRANSFER");
        values.forEach(value -> value.actions().forEach(action -> {
            if ("REALLOCATION".equals(action.actionType())) {
                assertThat(action.sourceWarehouseId()).isEqualTo(action.destinationWarehouseId());
                assertThat(action.sourceSalesPointId()).isNotEqualTo(action.targetSalesPointId());
            }
            if ("RT_TRANSFER".equals(action.actionType())) {
                assertThat(action.sourceWarehouseId()).isNotEqualTo(action.destinationWarehouseId());
            }
        }));
        assertThat(values).filteredOn(value -> value.actions().stream()
                        .anyMatch(action -> action.sourceWarehouseId() != null))
                .allSatisfy(value -> {
                    assertThat(value.inventorySnapshots()).hasSize(2);
                    BigDecimal moved = value.actions().stream()
                            .filter(action -> action.sourceWarehouseId() != null)
                            .map(StrategyStatisticsDemoAction::actionQuantity)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    assertThat(value.performance().stream()
                            .map(StrategyStatisticsDemoPerformance::movedQuantity)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(moved);
                });
    }

    private static StrategyStatisticsDemoDimensions dimensions() {
        NavigableMap<LocalDate, StrategyStatisticsDemoSalesDay> point201 = dailySales();
        NavigableMap<LocalDate, StrategyStatisticsDemoSalesDay> point202 = dailySales();
        return new StrategyStatisticsDemoDimensions(
                List.of(
                        new StrategyStatisticsDemoSalesCandidate(101L, 201L, point201,
                                BigDecimal.valueOf(1000), BigDecimal.valueOf(2200)),
                        new StrategyStatisticsDemoSalesCandidate(101L, 202L, point202,
                                BigDecimal.valueOf(1000), BigDecimal.valueOf(2200))
                ),
                List.of(
                        inventory(1L, 201L, 401L),
                        inventory(2L, 202L, 401L),
                        inventory(3L, 301L, 402L)
                ),
                21L,
                81L
        );
    }

    private static NavigableMap<LocalDate, StrategyStatisticsDemoSalesDay> dailySales() {
        NavigableMap<LocalDate, StrategyStatisticsDemoSalesDay> result = new TreeMap<>();
        for (LocalDate date = LocalDate.of(2025, 8, 18);
                !date.isAfter(LocalDate.of(2026, 8, 23)); date = date.plusDays(1)) {
            result.put(date, new StrategyStatisticsDemoSalesDay(
                    date, BigDecimal.ONE, BigDecimal.valueOf(2200)));
        }
        return result;
    }

    private static StrategyStatisticsDemoInventoryCandidate inventory(long id, long pointId, long warehouseId) {
        return new StrategyStatisticsDemoInventoryCandidate(
                id, 101L, 500L + id, pointId, warehouseId,
                BigDecimal.valueOf(100), BigDecimal.valueOf(90), BigDecimal.TEN,
                BigDecimal.ONE, BigDecimal.TEN, LocalDate.of(2027, 1, 1));
    }
}
