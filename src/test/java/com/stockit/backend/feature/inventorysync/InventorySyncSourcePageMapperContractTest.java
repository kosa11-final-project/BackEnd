package com.stockit.backend.feature.inventorysync;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class InventorySyncSourcePageMapperContractTest {

    private static final Path MAPPER = Path.of("src/main/resources/mappers/inventorysync/InventorySyncSourcePageMapper.xml");

    @Test
    void preflightChecksBothDirectionsOfSourceToCanonicalMapping() throws IOException {
        String sql = Files.readString(MAPPER);

        assertThat(sql)
                .contains("NOT EXISTS (\n                           SELECT 1 FROM inventory_source_row_map m")
                .contains("m.mapping_status != 'MAPPED'")
                .contains("GROUP BY m.inventory_balance_id")
                .contains("HAVING COUNT(*) > 1")
                .contains("m.sku_id IS NULL OR m.warehouse_id IS NULL OR m.inventory_balance_id IS NULL")
                .contains("NOT EXISTS (\n                               SELECT 1 FROM inventory_source_offline src")
                .contains("NOT EXISTS (\n                               SELECT 1 FROM inventory_source_ecommerce src")
                .contains("NOT EXISTS (\n                               SELECT 1 FROM inventory_source_greeting src")
                .contains("NOT EXISTS (\n                               SELECT 1 FROM inventory_source_warehouse src");
    }

    @Test
    void pageReaderOnlyRunsAfterMappedPreflightAndUsesChangedHashProjection() throws IOException {
        String sql = Files.readString(MAPPER);

        assertThat(sql)
                .contains("SELECT m.source_type, m.source_record_key, m.product_id, category.category_id, m.sku_id")
                .contains("LEFT JOIN category category ON category.category_name = src.category_name")
                .contains("COALESCE(category_small_name, category_middle_name, category_large_name) AS category_name")
                .contains("TRIM(REGEXP_SUBSTR(display_category_path, '[^&gt;]+$')) AS category_name")
                .contains("src.product_name, src.brand_name, src.product_status, src.sale_available_yn")
                .contains("src.selling_price, src.actual_price, src.product_cost")
                .contains("src.manufactured_date, src.received_date, src.expiry_date, src.sale_stop_date")
                .contains("src.safety_stock_qty, src.target_stock_qty")
                .contains("AND m.mapping_status = 'MAPPED' AND m.is_deleted = 0")
                .contains("AND (src.synced_record_hash IS NULL OR src.record_hash != src.synced_record_hash)")
                .contains("FETCH NEXT #{limit} ROWS ONLY");
    }
}
