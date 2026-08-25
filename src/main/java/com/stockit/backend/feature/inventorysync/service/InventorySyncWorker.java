package com.stockit.backend.feature.inventorysync.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.stockit.backend.feature.inventorysync.adapter.CanonicalInventoryRecord;
import com.stockit.backend.feature.inventorysync.adapter.InventorySourceAdapter;
import com.stockit.backend.feature.inventorysync.batch.InventorySyncAttemptBuffer;
import com.stockit.backend.feature.inventorysync.InventorySyncSourceOrder;
import com.stockit.backend.feature.inventorysync.mapper.InventorySyncRunMapper;
import com.stockit.backend.feature.inventorysync.mapper.InventorySyncSourcePageMapper;
import com.stockit.backend.feature.inventorysync.mapper.InventorySyncRunSourceMapper;

@Component
public class InventorySyncWorker {
    private static final int PAGE_SIZE = 500;
    private final InventorySyncRunMapper runMapper;
    private final InventorySyncSourcePageMapper sourceMapper;
    private final InventorySyncPublisher publisher;
    private final InventorySyncPublisher.CanonicalBatchWriter writer;
    private final InventorySyncRunSourceMapper runSourceMapper;
    private final InventorySyncRunControlService runControl;
    private final Map<String, InventorySourceAdapter> adapters;
    private final String workerInstanceId;

    @Autowired
    public InventorySyncWorker(
            InventorySyncRunMapper runMapper,
            InventorySyncSourcePageMapper sourceMapper,
            InventorySyncPublisher publisher,
            InventorySyncPublisher.CanonicalBatchWriter writer,
            InventorySyncRunSourceMapper runSourceMapper,
            InventorySyncRunControlService runControl,
            List<InventorySourceAdapter> adapters,
            @Value("${app.inventory-sync.instance-id:local-worker}") String workerInstanceId
    ) {
        this.runMapper = runMapper;
        this.sourceMapper = sourceMapper;
        this.publisher = publisher;
        this.writer = writer;
        this.runSourceMapper = runSourceMapper;
        this.runControl = runControl;
        this.adapters = adapters.stream().collect(Collectors.toUnmodifiableMap(InventorySourceAdapter::sourceType, Function.identity()));
        this.workerInstanceId = workerInstanceId == null || workerInstanceId.isBlank() ? "local-worker" : workerInstanceId;
    }

    public InventorySyncWorker(
            InventorySyncRunMapper runMapper,
            InventorySyncSourcePageMapper sourceMapper,
            InventorySyncPublisher publisher,
            InventorySyncPublisher.CanonicalBatchWriter writer,
            InventorySyncRunSourceMapper runSourceMapper,
            InventorySyncRunControlService runControl,
            List<InventorySourceAdapter> adapters
    ) {
        this(runMapper, sourceMapper, publisher, writer, runSourceMapper, runControl, adapters, "local-worker");
    }

    public void execute(Long runId) {
        var run = runMapper.selectById(runId);
        if (run == null) {
            throw new InventorySyncWorkerFailedException("inventory sync run not found: " + runId, null);
        }
        final int attemptNo = run.getMainAttemptNo();
        final long fencingToken = run.getFencingToken();
        String currentSourceType = "OFFLINE";
        try {
            if (runMapper.markRunning(runId, attemptNo, fencingToken, instanceId(), Instant.now().plusSeconds(300)) == 0) {
                throw new InventorySyncWorkerFailedException("inventory sync run claim was fenced: " + runId, null);
            }
            InventorySyncAttemptBuffer buffer = new InventorySyncAttemptBuffer(String.valueOf(runId), attemptNo, fencingToken,
                    InventorySyncAttemptBuffer.DEFAULT_MAX_RECORDS, run.getRequestedBy());
            runSourceMapper.insertPendingSources(runId);
            long read = 0;
            List<String> sourceTypes = runMapper.selectSourceTypes();
            if (sourceTypes == null || !new HashSet<>(sourceTypes).equals(new HashSet<>(InventorySyncSourceOrder.TYPES))) {
                throw new IllegalStateException("SOURCE_SCHEMA_INVALID: expected exactly four source state rows");
            }
            for (String sourceType : InventorySyncSourceOrder.TYPES) {
                currentSourceType = sourceType;
                updatePhaseOrThrow(runId, attemptNo, fencingToken, "VALIDATING", read, buffer.size());
                if (sourceMapper.countInvalidOrUnmapped(sourceType) > 0) {
                    throw new IllegalStateException("SOURCE_MAPPING_INVALID:" + sourceType);
                }
                updatePhaseOrThrow(runId, attemptNo, fencingToken, "READING", read, buffer.size());
                long sourceVersion = runMapper.selectSourceVersion(sourceType);
                buffer.recordSourceVersion(sourceType, sourceVersion);
                runSourceMapper.insertRunSource(runId, sourceType, sourceVersion);
                String lastSourceRecordKey = null;
                long sourceRead = 0;
                int sourceMappedStart = buffer.size();
                for (;;) {
                    List<CanonicalInventoryRecord> page = sourceMapper.selectCanonicalPage(sourceType, sourceVersion, lastSourceRecordKey, PAGE_SIZE);
                    if (page.isEmpty()) break;
                    InventorySourceAdapter adapter = adapters.get(sourceType);
                    if (adapter == null) throw new IllegalStateException("unsupported source type: " + sourceType);
                    page.forEach(record -> adapter.accept(record, buffer));
                    read += page.size();
                    sourceRead += page.size();
                    lastSourceRecordKey = page.get(page.size() - 1).sourceRecordKey();
                    updatePhaseOrThrow(runId, attemptNo, fencingToken, "NORMALIZING", read, buffer.size());
                    if (runMapper.heartbeat(runId, attemptNo, fencingToken, instanceId(), Instant.now().plusSeconds(300)) != 1) {
                        throw new IllegalStateException("STALE_FENCING");
                    }
                    runSourceMapper.updateProgress(runId, sourceType, sourceRead, buffer.size() - sourceMappedStart);
                    if (page.size() < PAGE_SIZE) break;
                }
                if (runMapper.selectSourceVersion(sourceType) != sourceVersion) {
                    throw new IllegalStateException("SOURCE_CHANGED:" + sourceType);
                }
            }
            updatePhaseOrThrow(runId, attemptNo, fencingToken, "PUBLISHING", read, buffer.size());
            if (runMapper.heartbeat(runId, attemptNo, fencingToken, instanceId(), Instant.now().plusSeconds(300)) != 1) {
                throw new IllegalStateException("STALE_FENCING");
            }
            publisher.publish(
                    buffer,
                    (id, attempt, token) -> {
                        if (runMapper.assertWritable(runId, attempt, token) != 1) {
                            throw new IllegalStateException("STALE_FENCING");
                        }
                        if (runMapper.heartbeat(runId, attempt, token, instanceId(), Instant.now().plusSeconds(300)) != 1) {
                            throw new IllegalStateException("STALE_FENCING");
                        }
                    },
                    writer,
                    (publishedBuffer, result) -> {
                        if (runMapper.complete(runId, attemptNo, fencingToken, "SUCCEEDED", result.changedCount(), null, null) != 1) {
                            throw new IllegalStateException("STALE_FENCING");
                        }
                    }
            );
        } catch (Exception exception) {
            String message = safeMessage(exception);
            String status = message.startsWith("SOURCE_CHANGED") ? "SOURCE_CHANGED" : "FAILED";
            String code = "SOURCE_CHANGED".equals(status) ? "SOURCE_CHANGED" : "SYNC_FAILED";
            runControl.markWorkerFailed(runId, currentSourceType, "MAIN", code, message, attemptNo, fencingToken, status);
            // Keep Spring Batch's persistent JobExecution aligned with the durable business run.
            // The control transaction above records the business failure; rethrowing makes the
            // tasklet fail as well instead of leaving Batch metadata as COMPLETED.
            throw new InventorySyncWorkerFailedException(message, exception);
        }
    }

    public static final class InventorySyncWorkerFailedException extends RuntimeException {
        public InventorySyncWorkerFailedException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private String instanceId() { return workerInstanceId; }
    private void updatePhaseOrThrow(Long runId, int attemptNo, long fencingToken, String phase,
                                    long readCount, long mappedCount) {
        if (runMapper.updatePhase(runId, attemptNo, fencingToken, phase, readCount, mappedCount) != 1) {
            throw new IllegalStateException("STALE_FENCING");
        }
    }
    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null ? exception.getClass().getSimpleName() : message.substring(0, Math.min(2000, message.length()));
    }
}
