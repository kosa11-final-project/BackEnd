package com.stockit.backend.feature.statistics.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.Test;

class StrategyStatisticsDemoDataFactoryTest {
    private final StrategyStatisticsDemoDataFactory factory = new StrategyStatisticsDemoDataFactory();
    private final StrategyStatisticsDemoDimensions dimensions = new StrategyStatisticsDemoDimensions(
            List.of(101L, 102L, 103L),
            List.of(201L, 202L),
            List.of(301L, 302L),
            List.of(401L, 402L),
            21L,
            81L
    );

    @Test
    void createsDeterministicCompletedStrategiesForEveryDate() {
        LocalDate fromDate = LocalDate.of(2026, 2, 23);
        LocalDate toDate = LocalDate.of(2026, 3, 8);

        List<StrategyStatisticsDemoData> first = factory.create(fromDate, toDate, dimensions);
        List<StrategyStatisticsDemoData> second = factory.create(fromDate, toDate, dimensions);

        assertThat(first).isEqualTo(second);
        assertThat(first).isNotEmpty();
        assertThat(first).allSatisfy(value -> {
            assertThat(value.endDate()).isBetween(fromDate, toDate);
            assertThat(value.startDate()).isBefore(value.endDate());
            assertThat(value.goalTargetValue()).isPositive();
            assertThat(value.goalActualValue()).isPositive();
            assertThat(value.achievementRate()).isBetween(
                    BigDecimal.valueOf(70),
                    BigDecimal.valueOf(120)
            );
            assertThat(value.endRiskStockQty()).isLessThan(value.startRiskStockQty());
            assertThat(value.endExpectedDisposalQty()).isLessThan(value.startExpectedDisposalQty());
            assertThat(value.estimatedLossSavingsAmount()).isPositive();
            assertThat(value.actions()).isNotEmpty();
        });
        assertThat(first.stream().map(StrategyStatisticsDemoData::caseId))
                .doesNotHaveDuplicates();
        assertThat(first.stream().map(StrategyStatisticsDemoData::resultId))
                .doesNotHaveDuplicates();
    }

    @Test
    void includesSingleAndCombinedActionsAcrossStoreAndWarehouseScopes() {
        List<StrategyStatisticsDemoData> values = factory.create(
                LocalDate.of(2026, 2, 23),
                LocalDate.of(2026, 3, 31),
                dimensions
        );

        assertThat(values).anySatisfy(value -> assertThat(value.actions()).hasSize(1));
        assertThat(values).anySatisfy(value -> assertThat(value.actions()).hasSizeGreaterThan(1));
        assertThat(values.stream()
                .flatMap(value -> value.actions().stream())
                .map(StrategyStatisticsDemoAction::actionType))
                .contains("PRICE_DISCOUNT", "REALLOCATION", "CHANNEL_EXPANSION", "RT_TRANSFER");
        assertThat(values.stream()
                .flatMap(value -> value.actions().stream())
                .filter(action -> action.sourceWarehouseId() != null)
                .toList()).isNotEmpty();
        assertThat(new HashSet<>(values.stream()
                .map(StrategyStatisticsDemoData::requestedSalesPointId)
                .toList())).contains(201L, 202L, 301L, 302L);
    }
}
