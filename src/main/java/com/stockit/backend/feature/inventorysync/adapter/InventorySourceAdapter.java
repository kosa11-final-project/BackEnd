package com.stockit.backend.feature.inventorysync.adapter;

import com.stockit.backend.feature.inventorysync.batch.InventorySyncAttemptBuffer;

/** 원천별 컬럼을 canonical typed record로 정제하는 adapter 계약입니다. */
public interface InventorySourceAdapter {

    String sourceType();

    default void accept(CanonicalInventoryRecord record, InventorySyncAttemptBuffer buffer) {
        if (!sourceType().equals(record.sourceType())) {
            throw new IllegalArgumentException("source type mismatch: expected " + sourceType());
        }
        buffer.add(record);
    }
}
