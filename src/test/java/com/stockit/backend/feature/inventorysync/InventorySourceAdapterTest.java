package com.stockit.backend.feature.inventorysync;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.stockit.backend.feature.inventorysync.adapter.CanonicalInventoryRecord;
import com.stockit.backend.feature.inventorysync.adapter.EcommerceInventorySourceAdapter;
import com.stockit.backend.feature.inventorysync.adapter.GreetingInventorySourceAdapter;
import com.stockit.backend.feature.inventorysync.adapter.InventorySourceAdapter;
import com.stockit.backend.feature.inventorysync.adapter.OfflineInventorySourceAdapter;
import com.stockit.backend.feature.inventorysync.adapter.WarehouseInventorySourceAdapter;
import com.stockit.backend.feature.inventorysync.batch.InventorySyncAttemptBuffer;

class InventorySourceAdapterTest {

    @Test
    void sourceSpecificAdaptersAcceptOnlyTheirOwnTypedRecord() {
        List<InventorySourceAdapter> adapters = List.of(
                new OfflineInventorySourceAdapter(),
                new EcommerceInventorySourceAdapter(),
                new GreetingInventorySourceAdapter(),
                new WarehouseInventorySourceAdapter()
        );

        for (InventorySourceAdapter adapter : adapters) {
            InventorySyncAttemptBuffer buffer = new InventorySyncAttemptBuffer("RUN-ADAPTER", 0, 1);
            CanonicalInventoryRecord matching = record(adapter.sourceType());
            assertDoesNotThrow(() -> adapter.accept(matching, buffer));
            assertThrows(IllegalArgumentException.class, () -> adapter.accept(record("OTHER"), buffer));
        }
    }

    private static CanonicalInventoryRecord record(String sourceType) {
        return new CanonicalInventoryRecord(
                sourceType, sourceType + ":ROW-1", 1L, 1L, null, 1L, 1L, 1L,
                null, null, null, BigDecimal.TEN, BigDecimal.ONE, "a".repeat(64), 1, 1
        );
    }
}
