package com.stockit.backend.feature.inventory.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.stockit.backend.feature.inventory.dto.request.InventoryQueryRequest;
import com.stockit.backend.feature.inventory.dto.response.InventoryListResponse;
import com.stockit.backend.feature.inventory.service.InventoryQueryService;
import com.stockit.backend.feature.inventory.vo.InventoryItemVO;
import com.stockit.backend.feature.inventory.vo.InventoryQuery;
import com.stockit.backend.feature.inventory.vo.InventorySummaryVO;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "INVENTORY_ORACLE_TEST", matches = "true")
class InventoryOracleMapperTest {

    @Autowired
    private InventoryMapper inventoryMapper;

    @Autowired
    private InventoryQueryService inventoryQueryService;

    @DynamicPropertySource
    static void oracleProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("DB_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("DB_PASSWORD"));
        registry.add("spring.datasource.hikari.schema", () -> "KOSA");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Test
    void readsInventoryListCountAndSummaryFromExistingTables() {
        InventoryQuery query = new InventoryQueryRequest().toQuery(LocalDate.of(2026, 8, 14));

        long count = inventoryMapper.countInventory(query);
        var items = inventoryMapper.selectInventoryList(query);
        InventorySummaryVO summary = inventoryMapper.selectInventorySummary(query);

        assertThat(count).isPositive();
        assertThat(items).isNotEmpty();
        InventoryItemVO first = items.get(0);
        assertThat(first.getSkuCode()).isNotBlank();
        assertThat(first.getCurrentQty()).isNotNull();
        assertThat(summary).isNotNull();
        assertThat(summary.getTotalCurrentQty()).isNotNull();

        InventoryListResponse response = inventoryQueryService.find(query);
        assertThat(response.items()).isNotEmpty();
        assertThat(response.items().get(0).rowId()).isNotBlank();
        assertThat(response.items().get(0).salesPoints()).isNotEmpty();
        assertThat(response.items().get(0).locations()).isNotEmpty();
        assertThat(response.items().get(0).ownerSalesPointCount())
                .isNotNull()
                .isGreaterThanOrEqualTo(response.items().get(0).salesPoints().size());
        assertThat(response.items())
                .extracting(item -> item.skuCode())
                .doesNotHaveDuplicates();

        var filterOptions = inventoryQueryService.filterOptions();
        assertThat(filterOptions.warehouses()).isNotEmpty();
        assertThat(filterOptions.categories()).isNotEmpty();
        assertThat(filterOptions.storageTypes())
                .extracting(option -> option.code())
                .contains("ROOM_TEMP");

        var firstItem = response.items().get(0);
        assertThat(firstItem.salesPoints().stream()
                .map(point -> point.currentQuantity() == null ? BigDecimal.ZERO : point.currentQuantity())
                .reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(firstItem.currentQuantity());
        if (firstItem.availableQuantity() != null) {
            assertThat(firstItem.salesPoints().stream()
                    .map(point -> point.availableQuantity() == null ? BigDecimal.ZERO : point.availableQuantity())
                    .reduce(BigDecimal.ZERO, BigDecimal::add))
                    .isEqualByComparingTo(firstItem.availableQuantity());
        }
        if (firstItem.reservedQuantity() != null) {
            assertThat(firstItem.salesPoints().stream()
                    .map(point -> point.reservedQuantity() == null ? BigDecimal.ZERO : point.reservedQuantity())
                    .reduce(BigDecimal.ZERO, BigDecimal::add))
                    .isEqualByComparingTo(firstItem.reservedQuantity());
        }
        String skuCode = firstItem.skuCode();
        String salesPointCode = firstItem.salesPoints().get(0).salesPointCode();

        InventoryItemVO detail = inventoryMapper.selectInventoryDetail(
                skuCode, salesPointCode, query.asOfDate()
        );
        assertThat(detail).isNotNull();
        assertThat(detail.getSkuCode()).isEqualTo(skuCode);

        var lots = inventoryMapper.selectInventoryLots(
                skuCode, salesPointCode, query.asOfDate()
        );
        assertThat(lots).isNotEmpty();
        assertThat(lots.get(0).getFefoPriority()).isEqualTo(1);
    }
}
