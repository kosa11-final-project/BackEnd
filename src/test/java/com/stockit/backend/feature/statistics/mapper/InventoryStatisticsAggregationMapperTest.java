package com.stockit.backend.feature.statistics.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import com.stockit.backend.feature.statistics.vo.InventoryStatisticsAggregateVO;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.sales-daily-export.output-path=./build/exports/sales_daily.csv"
)
@ActiveProfiles("test")
@Sql("classpath:statistics/inventory_statistics_aggregation_test_schema.sql")
class InventoryStatisticsAggregationMapperTest {

    @Autowired
    private InventoryStatisticsAggregationMapper mapper;

    @Test
    void subtractsSharedForecastOnceWhenInventoryIsSplitAcrossLocations() {
        InventoryStatisticsAggregateVO national = mapper
                .selectScopeAggregates(LocalDate.of(2026, 8, 17))
                .stream()
                .filter(scope -> "NATIONAL".equals(scope.getScopeType()))
                .filter(scope -> "ALL".equals(scope.getScopeCode()))
                .findFirst()
                .orElseThrow();

        assertThat(national.getExpectedDisposalQty30d())
                .isEqualByComparingTo(new BigDecimal("40"));
        assertThat(national.getExpectedDisposalLossAmount30d())
                .isEqualByComparingTo(new BigDecimal("200"));
    }
}
