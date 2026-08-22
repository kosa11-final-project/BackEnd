package com.stockit.backend.feature.inventorysync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.stockit.backend.feature.inventorysync.adapter.CanonicalInventoryRecord;
import com.stockit.backend.feature.inventorysync.batch.InventorySyncAttemptBuffer;
import com.stockit.backend.feature.inventorysync.service.InventorySyncPublisher;

/**
 * Oracle가 연결되지 않은 로컬에서도 production substrate의 capacity 계약을 검증한다.
 * redo/undo/lock/p95는 승인된 Oracle Gate에서 같은 production mapper/publisher로 측정한다.
 */
class InventorySyncOracleCapacityIT {

    @Test
    void deduplicatesTargetRowsAndCollectsRiskScopesAtThe100kGate() {
        InventorySyncAttemptBuffer buffer = new InventorySyncAttemptBuffer("RUN-1", 0, 1, 100_000);
        for (int i = 1; i <= 100_000; i++) {
            buffer.add(record(i));
        }
        assertEquals(100_000, buffer.size());
        assertEquals(100_000, buffer.riskScopes().size());
        assertThrows(IllegalStateException.class, () -> buffer.add(record(100_001)));
    }

    @Test
    void publisherUses500RowBatchesAndChecksFencingBetweenBatches() {
        int rowCount = 100_000;
        InventorySyncAttemptBuffer buffer = new InventorySyncAttemptBuffer("RUN-2", 1, 7, rowCount);
        for (int i = 1; i <= rowCount; i++) {
            buffer.add(record(i));
        }
        var batches = new int[]{0};
        var fencingChecks = new int[]{0};
        InventorySyncPublisher.PublishResult result = new InventorySyncPublisher().publish(
                buffer,
                (runId, attemptNo, token) -> fencingChecks[0]++,
                (runId, rows, actorId) -> {
                    batches[0]++;
                    org.junit.jupiter.api.Assertions.assertTrue(
                            rows.size() > 0 && rows.size() <= InventorySyncPublisher.BATCH_SIZE,
                            "capacity fixture must never exceed 500-row batches");
                    return rows.size();
                }
        );
        // 100,000 / 500 = 200 full batches; the check runs before, between, and after them.
        assertEquals(200, batches[0]);
        assertEquals(rowCount, result.changedCount());
        assertEquals(202, fencingChecks[0]);
    }

    private static CanonicalInventoryRecord record(long id) {
        return new CanonicalInventoryRecord(
                "WAREHOUSE", "WAREHOUSE:W1:SKU-" + id + ":LOT-1", 1L, id,
                null, 1L, 1L, id, null, null, null,
                BigDecimal.TEN, BigDecimal.ONE, String.format("%064d", id), 1, 1
        );
    }
}
