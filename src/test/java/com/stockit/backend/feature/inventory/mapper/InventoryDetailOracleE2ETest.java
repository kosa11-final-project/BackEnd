package com.stockit.backend.feature.inventory.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "INVENTORY_DETAIL_ORACLE_E2E", matches = "true")
class InventoryDetailOracleE2ETest {

    @Autowired
    private InventoryMapper inventoryMapper;

    @DynamicPropertySource
    static void oracleProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("DB_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("DB_PASSWORD"));
        registry.add("spring.datasource.hikari.schema", () -> "KOSA");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("app.inventory-sync.batch-enabled", () -> "false");
        registry.add("app.inventory-sync.schedule.enabled", () -> "false");
    }

    @Test
    void readsTheUnassignedInventoryDetailAndLotsWithoutOracleSqlErrors() {
        LocalDate asOfDate = LocalDate.now();

        assertThat(inventoryMapper.selectInventoryDetail("SKU000025", "UNASSIGNED", asOfDate))
                .isNotNull();
        assertThat(inventoryMapper.selectInventoryLots("SKU000025", "UNASSIGNED", asOfDate))
                .isNotNull();
    }
}
