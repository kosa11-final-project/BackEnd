package com.stockit.backend.feature.strategy.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import com.stockit.backend.feature.statistics.service.StrategyExecutionResultService;
import com.stockit.backend.feature.strategy.mapper.StrategyPerformanceSyncMapper;

@SpringBootTest
@ActiveProfiles("test")
@Sql(
        scripts = "/strategy/strategy-execution-mapper-test-schema.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
class StrategyPerformanceSyncServiceIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-08-26T06:30:00Z");
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 8, 26);

    @DynamicPropertySource
    static void useIsolatedDatabase(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                () -> "jdbc:h2:mem:strategy-performance-sync-service;MODE=Oracle;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
        );
    }

    @Autowired
    private StrategyPerformanceSyncMapper mapper;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Test
    @Transactional
    void restoresAndUpdatesSoftDeletedPerformanceWithTheSameDailyKey() {
        jdbcTemplate.update("""
                INSERT INTO strategy_performance (
                    strategy_performance_id,
                    strategy_option_id,
                    performance_date,
                    actual_sales_qty,
                    actual_revenue,
                    actual_contribution_margin,
                    actual_remaining_qty,
                    created_at,
                    created_by,
                    is_deleted
                ) VALUES (
                    6999,
                    1001,
                    :performanceDate,
                    999,
                    999,
                    999,
                    999,
                    TIMESTAMP '2026-08-01 00:00:00',
                    99,
                    1
                )
                """, Map.of("performanceDate", BUSINESS_DATE));
        StrategyExecutionResultService resultService = mock(StrategyExecutionResultService.class);
        StrategyPerformanceSyncServiceImpl service = new StrategyPerformanceSyncServiceImpl(
                mapper,
                resultService,
                Clock.fixed(NOW, ZoneId.of("Asia/Seoul"))
        );

        service.synchronize(7L);

        Map<String, Object> restored = jdbcTemplate.queryForMap("""
                SELECT actual_sales_qty,
                       actual_revenue,
                       actual_contribution_margin,
                       actual_remaining_qty,
                       created_by,
                       updated_by,
                       is_deleted
                FROM strategy_performance
                WHERE strategy_option_id = 1001
                  AND performance_date = :performanceDate
                """, Map.of("performanceDate", BUSINESS_DATE));
        Integer matchingRowCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM strategy_performance
                WHERE strategy_option_id = 1001
                  AND performance_date = :performanceDate
                """, Map.of("performanceDate", BUSINESS_DATE), Integer.class);

        assertThat(matchingRowCount).isEqualTo(1);
        assertThat((BigDecimal) restored.get("ACTUAL_SALES_QTY")).isEqualByComparingTo("0");
        assertThat((BigDecimal) restored.get("ACTUAL_REVENUE")).isEqualByComparingTo("0");
        assertThat((BigDecimal) restored.get("ACTUAL_CONTRIBUTION_MARGIN")).isEqualByComparingTo("0");
        assertThat((BigDecimal) restored.get("ACTUAL_REMAINING_QTY")).isEqualByComparingTo("80");
        assertThat(((Number) restored.get("CREATED_BY")).longValue()).isEqualTo(99L);
        assertThat(((Number) restored.get("UPDATED_BY")).longValue()).isEqualTo(7L);
        assertThat(((Number) restored.get("IS_DELETED")).intValue()).isZero();
        verify(resultService).process(BUSINESS_DATE);
    }
}
