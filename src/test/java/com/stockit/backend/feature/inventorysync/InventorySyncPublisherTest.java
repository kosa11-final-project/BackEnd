package com.stockit.backend.feature.inventorysync;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.stockit.backend.feature.inventorysync.adapter.CanonicalInventoryRecord;
import com.stockit.backend.feature.inventorysync.batch.InventorySyncAttemptBuffer;
import com.stockit.backend.feature.inventorysync.service.InventorySyncPublisher;

class InventorySyncPublisherTest {

    @Test
    void passesTheFinalChangedCountToTheFinishHook() {
        InventorySyncAttemptBuffer buffer = new InventorySyncAttemptBuffer("101", 1, 7L, 10, 9L);
        AtomicInteger finishedChangedCount = new AtomicInteger(-1);
        InventorySyncPublisher.CanonicalBatchWriter writer = new InventorySyncPublisher.CanonicalBatchWriter() {
            @Override
            public int writeBatch(String runId, List<CanonicalInventoryRecord> records, Long actorId) {
                return 0;
            }

            @Override
            public int writeReferenceTargets(String runId, InventorySyncAttemptBuffer ignored, Long actorId) {
                return 2;
            }

            @Override
            public void finish(String runId, Map<String, Long> sourceVersions, Set<String> riskScopes,
                               Long actorId, int changedCount) {
                finishedChangedCount.set(changedCount);
            }
        };

        var result = new InventorySyncPublisher().publish(buffer, (runId, attemptNo, token) -> { }, writer);

        assertThat(result.changedCount()).isEqualTo(2);
        assertThat(finishedChangedCount).hasValue(2);
    }
}
