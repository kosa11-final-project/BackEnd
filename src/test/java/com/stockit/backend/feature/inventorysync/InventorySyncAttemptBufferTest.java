package com.stockit.backend.feature.inventorysync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.stockit.backend.feature.inventorysync.adapter.CanonicalInventoryRecord;
import com.stockit.backend.feature.inventorysync.batch.InventorySyncAttemptBuffer;

class InventorySyncAttemptBufferTest {

    @Test
    void deduplicatesSharedCanonicalTargetsBeforePublishing() {
        InventorySyncAttemptBuffer buffer = new InventorySyncAttemptBuffer("RUN-1", 0, 1);

        buffer.add(record("OFFLINE", 1L, "상품", LocalDate.of(2026, 1, 1)));
        buffer.add(record("OFFLINE", 2L, "상품", LocalDate.of(2026, 1, 1)));

        assertThat(buffer.records()).hasSize(2);
        assertThat(buffer.productRecords()).hasSize(1);
        assertThat(buffer.skuRecords()).hasSize(1);
        assertThat(buffer.lotRecords()).hasSize(1);
    }

    @Test
    void failsBeforeWriteWhenTwoRowsDisagreeOnTheSameProduct() {
        InventorySyncAttemptBuffer buffer = new InventorySyncAttemptBuffer("RUN-2", 0, 1);
        buffer.add(record("OFFLINE", 1L, "상품 A", LocalDate.of(2026, 1, 1)));

        assertThatThrownBy(() -> buffer.add(record("ECOMMERCE", 2L, "상품 B", LocalDate.of(2026, 1, 1))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("conflicting PRODUCT");
    }

    @Test
    void warehouseOwnsPhysicalLotDates() {
        InventorySyncAttemptBuffer buffer = new InventorySyncAttemptBuffer("RUN-3", 0, 1);
        buffer.add(record("OFFLINE", 1L, "상품", LocalDate.of(2026, 1, 1)));
        buffer.add(record("WAREHOUSE", 2L, "상품", LocalDate.of(2026, 2, 1)));

        assertThat(buffer.lotRecords()).singleElement()
                .extracting(CanonicalInventoryRecord::manufacturedDate)
                .isEqualTo(LocalDate.of(2026, 2, 1));
    }

    @Test
    void ignoresMissingOrDifferentSourceLotStatusesBecauseCanonicalStatusIsDerivedDuringSync() {
        InventorySyncAttemptBuffer buffer = new InventorySyncAttemptBuffer("RUN-4", 0, 1);

        buffer.add(record("OFFLINE", 1L, "상품", LocalDate.of(2026, 1, 1), "AVAILABLE"));
        buffer.add(record("ECOMMERCE", 2L, "상품", LocalDate.of(2026, 1, 1), null));

        assertThat(buffer.lotRecords()).hasSize(1);
        assertThat(buffer.records())
                .extracting(CanonicalInventoryRecord::lotStatus)
                .containsOnlyNulls();
    }

    private CanonicalInventoryRecord record(String sourceType, long balanceId, String productName,
                                              LocalDate manufacturedDate) {
        return record(sourceType, balanceId, productName, manufacturedDate, "AVAILABLE");
    }

    private CanonicalInventoryRecord record(String sourceType, long balanceId, String productName,
                                              LocalDate manufacturedDate, String lotStatus) {
        return new CanonicalInventoryRecord(
                sourceType, sourceType + ":ROW-" + balanceId,
                1L, 1L, 1L, null, 1L, 1L, balanceId,
                null, null, null,
                productName, "브랜드", "ACTIVE", "WAREHOUSE".equals(sourceType) ? null : "Y",
                "SKU", new BigDecimal("100"), "G", BigDecimal.ONE, "EA", "FROZEN",
                null, null, null, null, null, null, null, null,
                manufacturedDate, LocalDate.of(2026, 2, 2), LocalDate.of(2027, 1, 1), null, lotStatus,
                null, null, BigDecimal.TEN, BigDecimal.ONE,
                String.format("%064d", balanceId), 1, 1
        );
    }
}
