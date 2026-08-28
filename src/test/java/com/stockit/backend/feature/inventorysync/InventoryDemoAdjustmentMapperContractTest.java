package com.stockit.backend.feature.inventorysync;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class InventoryDemoAdjustmentMapperContractTest {
    private static final Path MAPPER = Path.of(
            "src/main/resources/mappers/inventorysync/InventoryDemoAdjustmentMapper.xml"
    );

    @Test
    void bulkAdjustmentCapsEachDecreaseAtTheRowsCurrentQuantity() throws Exception {
        String sql = Files.readString(MAPPER);

        assertThat(sql).contains("id=\"countAdjustableSyncedRows\"");
        assertThat(sql).contains("available_stock_count &gt; 0");
        assertThat(sql).contains("sellable_quantity &gt; 0");
        assertThat(sql).contains("saleable_meal_count &gt; 0");
        assertThat(sql).contains("physical_available_qty &gt; 0");
        assertThat(sql).containsSubsequence(
                "LEAST(src.available_stock_count, #{decreaseQty})",
                "LEAST(src.sellable_quantity, #{decreaseQty})",
                "LEAST(src.saleable_meal_count, #{decreaseQty})",
                "LEAST(src.physical_available_qty, #{decreaseQty})"
        );
        assertThat(sql).contains("src.reserved_stock_count = LEAST(");
        assertThat(sql).contains("src.ordered_quantity = LEAST(");
        assertThat(sql).contains("src.committed_meal_count = LEAST(");
        assertThat(sql).contains("src.physical_reserved_qty = LEAST(");
        assertThat(sql).contains("'reservedQtyBefore' VALUE");
        assertThat(sql).contains("'reservedQtyAfter' VALUE");
    }
}
