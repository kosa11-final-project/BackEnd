package com.stockit.backend.common.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Sql(
        scripts = {
                "classpath:sku-cost/sku_cost_migration_test_prerequisites.sql",
                "classpath:db/migration/V14__create_sku_cost.sql",
                "classpath:db/migration/V15__backfill_sku_cost.sql"
        },
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS
)
class SkuCostMigrationTest {

    @DynamicPropertySource
    static void useIsolatedDatabase(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                () -> "jdbc:h2:mem:sku-cost;MODE=Oracle;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
        );
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsOneCurrentCostPerPricedSkuUsingDeterministicRules() {
        assertThat(count("SELECT COUNT(*) FROM sku_cost")).isEqualTo(3);
        assertThat(costOf(101L)).isEqualByComparingTo("8000");
        assertThat(costOf(102L)).isEqualByComparingTo("9000");
        assertThat(costOf(103L)).isEqualByComparingTo("12000");
        assertThat(count("SELECT COUNT(*) FROM sku_cost WHERE sku_id = 104")).isZero();
        assertThat(count("SELECT COUNT(*) FROM sku_cost WHERE sku_id = 105")).isZero();

        LocalDate effectiveFrom = jdbcTemplate.queryForObject(
                "SELECT effective_from FROM sku_cost WHERE sku_id = 101",
                (resultSet, rowNumber) -> resultSet.getObject(1, LocalDate.class)
        );
        assertThat(effectiveFrom).isEqualTo(LocalDate.now());
        assertThat(count("SELECT COUNT(*) FROM sku_cost WHERE effective_to IS NOT NULL")).isZero();
        assertThat(count("SELECT COUNT(*) FROM sku_cost WHERE created_by = 1 AND updated_by = 1")).isEqualTo(3);
    }

    @Test
    void keepsTheLegacyProductCostAvailableDuringTheTransition() {
        BigDecimal legacyCost = jdbcTemplate.queryForObject(
                "SELECT product_cost FROM sku_channel_price WHERE sku_channel_price_id = 1",
                BigDecimal.class
        );

        assertThat(legacyCost).isEqualByComparingTo("8000");
    }

    private long count(String sql) {
        return jdbcTemplate.queryForObject(sql, Long.class);
    }

    private BigDecimal costOf(Long skuId) {
        return jdbcTemplate.queryForObject(
                "SELECT unit_cost FROM sku_cost WHERE sku_id = ?",
                BigDecimal.class,
                skuId
        );
    }
}
