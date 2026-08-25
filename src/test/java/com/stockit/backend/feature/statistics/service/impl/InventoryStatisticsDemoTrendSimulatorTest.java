package com.stockit.backend.feature.statistics.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.stockit.backend.feature.statistics.dto.response.InventoryFinancialStatisticsResponse;
import com.stockit.backend.feature.statistics.dto.response.InventoryStatisticsDataQualityResponse;
import com.stockit.backend.feature.statistics.dto.response.InventoryStatisticsSummaryResponse;
import com.stockit.backend.feature.statistics.dto.response.RiskGradeStatisticsResponse;

class InventoryStatisticsDemoTrendSimulatorTest {

    private final InventoryStatisticsDemoTrendSimulator simulator =
            new InventoryStatisticsDemoTrendSimulator();

    @Test
    void keepsTheCurrentEndpointExactAndMakesPastRiskNaturallyHigher() {
        LocalDate from = LocalDate.of(2026, 2, 23);
        LocalDate to = LocalDate.of(2026, 8, 23);
        InventoryStatisticsSummaryResponse current = summary();

        InventoryStatisticsSummaryResponse endpoint = simulator.simulate(
                current, to, from, to, 1.2, "NATIONAL:ALL"
        );
        InventoryStatisticsSummaryResponse past = simulator.simulate(
                current, from, from, to, 1.0, "NATIONAL:ALL"
        );

        assertThat(endpoint).isSameAs(current);
        assertThat(past.criticalStockQty()).isGreaterThan(current.criticalStockQty());
        assertThat(past.expectedDisposalQty30d()).isGreaterThan(current.expectedDisposalQty30d());
        assertThat(past.riskDistribution())
                .extracting(RiskGradeStatisticsResponse::stockQty)
                .allMatch(value -> value.signum() >= 0);
        assertThat(past.riskDistribution().stream()
                .map(RiskGradeStatisticsResponse::stockQty)
                .reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(past.totalStockQty());
    }

    private static InventoryStatisticsSummaryResponse summary() {
        return new InventoryStatisticsSummaryResponse(
                100,
                new BigDecimal("10000"),
                new BigDecimal("9000"),
                10,
                new BigDecimal("1500"),
                8,
                new BigDecimal("300"),
                List.of(
                        new RiskGradeStatisticsResponse("CRITICAL", 10, new BigDecimal("1500")),
                        new RiskGradeStatisticsResponse("WARNING", 20, new BigDecimal("2500")),
                        new RiskGradeStatisticsResponse("NORMAL", 40, new BigDecimal("3500")),
                        new RiskGradeStatisticsResponse("GOOD", 25, new BigDecimal("2000")),
                        new RiskGradeStatisticsResponse("UNASSESSED", 5, new BigDecimal("500"))
                ),
                new InventoryStatisticsDataQualityResponse(
                        5, new BigDecimal("500"), 2, new BigDecimal("100")
                ),
                new InventoryFinancialStatisticsResponse(
                        new BigDecimal("10000000"),
                        new BigDecimal("1500000"),
                        new BigDecimal("300000"),
                        1,
                        new BigDecimal("50")
                )
        );
    }
}
