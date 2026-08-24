package com.stockit.backend.feature.inventorysync.service;

import java.util.List;

import org.springframework.transaction.annotation.Transactional;

import com.stockit.backend.feature.inventorysync.adapter.CanonicalInventoryRecord;
import com.stockit.backend.feature.inventorysync.batch.InventorySyncAttemptBuffer;

/**
 * U4에서 실제 MyBatis writer를 주입해 확장할 publish primitive입니다.
 * main business commit은 이 메서드 하나의 transaction에서만 일어나야 합니다.
 */
public class InventorySyncPublisher {

    public static final int BATCH_SIZE = 500;

    @Transactional
    public PublishResult publish(
            InventorySyncAttemptBuffer buffer,
            FencingGuard fencingGuard,
            CanonicalBatchWriter writer
    ) {
        return publish(buffer, fencingGuard, writer, (ignoredBuffer, ignoredResult) -> { });
    }

    @Transactional
    public PublishResult publish(
            InventorySyncAttemptBuffer buffer,
            FencingGuard fencingGuard,
            CanonicalBatchWriter writer,
            CompletionWriter completion
    ) {
        if (buffer == null || fencingGuard == null || writer == null || completion == null) {
            throw new IllegalArgumentException("publish dependencies must not be null");
        }
        fencingGuard.assertWritable(buffer.runId(), buffer.attemptNo(), buffer.fencingToken());
        writer.beforeWrite(buffer.runId(), buffer.sourceVersions());
        int changed = writer.writeReferenceTargets(buffer.runId(), buffer, buffer.requestedBy());
        List<CanonicalInventoryRecord> batch = new java.util.ArrayList<>(BATCH_SIZE);
        for (CanonicalInventoryRecord record : buffer.records()) {
            batch.add(record);
            if (batch.size() == BATCH_SIZE) {
                changed += writer.writeBatch(buffer.runId(), List.copyOf(batch), buffer.requestedBy());
                batch.clear();
                fencingGuard.assertWritable(buffer.runId(), buffer.attemptNo(), buffer.fencingToken());
            }
        }
        if (!batch.isEmpty()) {
            changed += writer.writeBatch(buffer.runId(), List.copyOf(batch), buffer.requestedBy());
        }
        changed += writer.finish(
                buffer.runId(), buffer.sourceVersions(), buffer.riskScopes(), buffer.requestedBy(), changed
        );
        fencingGuard.assertWritable(buffer.runId(), buffer.attemptNo(), buffer.fencingToken());
        PublishResult result = new PublishResult(buffer.size(), changed, buffer.riskScopes().size());
        completion.complete(buffer, result);
        return result;
    }

    public record PublishResult(int mappedCount, int changedCount, int riskScopeCount) { }

    @FunctionalInterface
    public interface CanonicalBatchWriter {
        /** total_qty를 직접 쓰지 않는 canonical changed-only batch writer. */
        int writeBatch(String runId, List<CanonicalInventoryRecord> records, Long actorId);

        default int writeReferenceTargets(String runId, InventorySyncAttemptBuffer buffer, Long actorId) { return 0; }

        default void beforeWrite(String runId, java.util.Map<String, Long> sourceVersions) { }

        default int finish(String runId, java.util.Map<String, Long> sourceVersions,
                           java.util.Set<String> riskScopes, Long actorId, int changedCount) { return 0; }
    }

    @FunctionalInterface
    public interface FencingGuard {
        void assertWritable(String runId, int attemptNo, long fencingToken);
    }

    @FunctionalInterface
    public interface CompletionWriter {
        void complete(InventorySyncAttemptBuffer buffer, PublishResult result);
    }
}
