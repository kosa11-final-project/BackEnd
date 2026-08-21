package com.stockit.backend.feature.dashboard.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;

import com.stockit.backend.feature.dashboard.vo.OfflineStoreInventoryVO;
import com.stockit.backend.feature.dashboard.vo.OnlineSalesPointInventoryVO;
import com.stockit.backend.feature.dashboard.vo.WarehouseInventoryVO;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.sales-daily-export.output-path=./build/exports/sales_daily.csv"
)
@ActiveProfiles("test")
@Sql("classpath:dashboard/dashboard_inventory_scope_test_schema.sql")
class DashboardMapperTest {

    private static final LocalDate AS_OF_DATE = LocalDate.of(2026, 8, 20);

    @DynamicPropertySource
    static void useIsolatedDatabase(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                () -> "jdbc:h2:mem:dashboard-scope;MODE=Oracle;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
        );
    }

    @Autowired
    private DashboardMapper mapper;

    @Test
    void separatesUnassignedOnlineAndOfflineInventoriesWithoutDoubleCounting() {
        WarehouseInventoryVO warehouse = mapper.selectWarehouseInventories(AS_OF_DATE)
                .stream()
                .filter(value -> "GYEONGIN_1".equals(value.getWarehouseCode()))
                .findFirst()
                .orElseThrow();
        OnlineSalesPointInventoryVO online = mapper.selectOnlineSalesPointInventories(AS_OF_DATE)
                .stream()
                .filter(value -> "GREETING".equals(value.getSalesPointCode()))
                .findFirst()
                .orElseThrow();
        OfflineStoreInventoryVO offline = mapper.selectOfflineStoreInventories(AS_OF_DATE)
                .stream()
                .filter(value -> "STORE-1".equals(value.getSalesPointCode()))
                .findFirst()
                .orElseThrow();

        assertThat(warehouse.getCurrentStock()).isEqualByComparingTo("10");
        assertThat(warehouse.getAvailableStock()).isEqualByComparingTo("10");

        assertThat(online.getCurrentStock()).isEqualByComparingTo("50");
        assertThat(online.getAvailableStock()).isEqualByComparingTo("50");
        assertThat(online.getStorageWarehouseCount()).isEqualTo(2);
        assertThat(online.getRiskSkuCount()).isEqualTo(1);
        assertThat(online.getExpectedDisposalQty()).isEqualByComparingTo("20");

        assertThat(offline.getCurrentStock()).isEqualByComparingTo("40");
        assertThat(offline.getAvailableStock()).isEqualByComparingTo("40");

        BigDecimal scopeTotal = warehouse.getAvailableStock()
                .add(online.getAvailableStock())
                .add(offline.getAvailableStock());
        assertThat(mapper.selectSummary(AS_OF_DATE).getTotalCurrentStock())
                .isEqualByComparingTo("100");
        assertThat(mapper.selectSummary(AS_OF_DATE).getTotalAvailableStock())
                .isEqualByComparingTo(scopeTotal)
                .isEqualByComparingTo("100");
    }
}
