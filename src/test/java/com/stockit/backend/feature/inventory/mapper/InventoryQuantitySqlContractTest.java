package com.stockit.backend.feature.inventory.mapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryQuantitySqlContractTest {

    private static final Path MAPPER_ROOT = Path.of("src/main/resources/mappers");

    @Test
    void inventoryQueriesNeverSubtractReservedQuantityFromOnHandQuantity() throws IOException {
        String inventorySql = read("inventory/InventoryMapper.xml");
        String forecastSql = read("demandforecast/DemandForecastMapper.xml");
        String riskSql = read("inventory/RiskAssessmentMapper.xml");

        assertThat(inventorySql)
                .doesNotContain("on_hand_qty - reserved_qty")
                .doesNotContain("ib.on_hand_qty - ib.reserved_qty");
        assertThat(forecastSql).doesNotContain("ib.on_hand_qty - ib.reserved_qty");
        assertThat(riskSql).doesNotContain("ib.on_hand_qty - ib.reserved_qty");
        assertThat(forecastSql)
                .doesNotContain("SALES_DAILY")
                .doesNotContain("sales_daily");
        assertThat(riskSql)
                .doesNotContain("SALES_DAILY")
                .doesNotContain("sales_daily");
        assertThat(forecastSql).contains("SUM(ib.on_hand_qty)");
        assertThat(riskSql)
                .contains("SUM(ib.on_hand_qty) AS on_hand_qty")
                .doesNotContain("SUM(ib.reserved_qty)");
        assertThat(forecastSql)
                .contains("#{salesPointCode} != 'UNASSIGNED' AND sp.sales_point_code = #{salesPointCode}")
                .contains("#{salesPointCode} = 'UNASSIGNED'");
    }

    @Test
    void inventoryCurrentQuantityIncludesAvailableAndReservedBuckets() throws IOException {
        String inventorySql = read("inventory/InventoryMapper.xml");

        assertThat(inventorySql).contains("SUM(on_hand_qty + reserved_qty) AS current_qty");
    }

    @Test
    void channelPriceQueryUsesEveryPriceColumnWithoutCopyingSellingPrice() throws IOException {
        String inventorySql = read("inventory/InventoryMapper.xml");

        assertThat(inventorySql)
                .contains("scp.actual_price")
                .contains("scp.minimum_selling_price")
                .doesNotContain("scp.selling_price AS actual_price")
                .doesNotContain("scp.selling_price AS minimum_selling_price");
    }

    @Test
    void sellerRiskAggregationPrioritizesTheMostSevereLot() throws IOException {
        String inventorySql = read("inventory/InventoryMapper.xml");

        assertThat(inventorySql)
                .contains("WHEN 'CRITICAL' THEN 1")
                .contains("WHEN 'WARNING' THEN 2")
                .contains("WHEN 'NORMAL' THEN 3")
                .contains("WHEN 'GOOD' THEN 4");
    }

    @Test
    void centerOnlyPolicyIsNotAppliedToNamedSalesPoint() throws IOException {
        String riskSql = read("inventory/RiskAssessmentMapper.xml");

        assertThat(riskSql)
                .contains("#{salesPointCode} != 'UNASSIGNED' AND sp.sales_point_code = #{salesPointCode}")
                .contains("#{salesPointCode} = 'UNASSIGNED'")
                .contains("ip.stock_sales_point_id IS NULL")
                .contains("ip.allocated_sales_point_id IS NULL");
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(MAPPER_ROOT.resolve(relativePath));
    }
}
