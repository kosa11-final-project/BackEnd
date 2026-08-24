package com.stockit.backend.feature.inventorysync.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.stockit.backend.feature.inventory.risk.RiskRuleEngine;
import com.stockit.backend.feature.inventorysync.InventorySyncSourceOrder;
import com.stockit.backend.feature.inventorysync.adapter.CanonicalInventoryRecord;
import com.stockit.backend.feature.inventorysync.mapper.InventorySyncCanonicalMapper;
import com.stockit.backend.feature.inventorysync.mapper.InventorySyncRunMapper;
import com.stockit.backend.feature.inventorysync.mapper.InventorySyncRunSourceMapper;
import com.stockit.backend.feature.inventorysync.mapper.InventorySyncSourceWriteMapper;
import com.stockit.backend.feature.inventorysync.risk.InventorySyncRiskScopeSnapshotLoader;
import com.stockit.backend.feature.inventorysync.risk.InventorySyncRiskWriter;

@Component
public class InventorySyncCanonicalBatchWriter implements InventorySyncPublisher.CanonicalBatchWriter {
    private final InventorySyncCanonicalMapper mapper;
    private final InventorySyncRunMapper runMapper;
    private final InventorySyncSourceWriteMapper sourceWriteMapper;
    private final InventorySyncRunSourceMapper runSourceMapper;
    private final InventorySyncRiskWriter riskWriter;
    private final InventorySyncRiskScopeSnapshotLoader riskSnapshotLoader;
    private final InventorySyncSnapshotCoordinator snapshotCoordinator;

    @Autowired
    public InventorySyncCanonicalBatchWriter(InventorySyncCanonicalMapper mapper,
                                             InventorySyncRunMapper runMapper,
                                             InventorySyncSourceWriteMapper sourceWriteMapper,
                                             InventorySyncRunSourceMapper runSourceMapper,
                                             InventorySyncRiskWriter riskWriter,
                                             InventorySyncRiskScopeSnapshotLoader riskSnapshotLoader,
                                             InventorySyncSnapshotCoordinator snapshotCoordinator) {
        this.mapper = mapper;
        this.runMapper = runMapper;
        this.sourceWriteMapper = sourceWriteMapper;
        this.runSourceMapper = runSourceMapper;
        this.riskWriter = riskWriter;
        this.riskSnapshotLoader = riskSnapshotLoader;
        this.snapshotCoordinator = snapshotCoordinator;
    }

    /** Backward-compatible constructor for focused writer tests that do not exercise snapshots. */
    public InventorySyncCanonicalBatchWriter(InventorySyncCanonicalMapper mapper,
                                             InventorySyncRunMapper runMapper,
                                             InventorySyncSourceWriteMapper sourceWriteMapper,
                                             InventorySyncRunSourceMapper runSourceMapper,
                                             InventorySyncRiskWriter riskWriter,
                                             InventorySyncRiskScopeSnapshotLoader riskSnapshotLoader) {
        this(mapper, runMapper, sourceWriteMapper, runSourceMapper, riskWriter, riskSnapshotLoader, null);
    }

    @Override
    public void beforeWrite(String runId, Map<String, Long> sourceVersions) {
        var run = runMapper.selectByIdForUpdate(Long.valueOf(runId));
        if (run == null || !"RUNNING".equals(run.getRunStatus())) {
            throw new IllegalStateException("STALE_FENCING");
        }
        for (String sourceType : sourceVersions.keySet().stream().sorted(InventorySyncSourceOrder.COMPARATOR).toList()) {
            Long lockedVersion = sourceWriteMapper.lockSourceState(sourceType);
            if (lockedVersion == null || !lockedVersion.equals(sourceVersions.get(sourceType))) {
                throw new IllegalStateException("SOURCE_CHANGED:" + sourceType);
            }
        }
    }

    @Override
    public int writeReferenceTargets(String runId, com.stockit.backend.feature.inventorysync.batch.InventorySyncAttemptBuffer buffer,
                                     Long actorId) {
        requireActor(actorId);
        int changed = 0;
        changed += writeBatches(buffer.productRecords(), rows -> mapper.updateProducts(rows, actorId));
        changed += writeBatches(buffer.skuRecords(), rows -> mapper.updateSkus(rows, actorId));
        changed += writeBatches(buffer.priceRecords(), rows -> mapper.updatePrices(rows, actorId));
        changed += writeBatches(buffer.skuCostRecords(), rows -> mapper.updateSkuCosts(rows, actorId));
        changed += writeBatches(buffer.lotRecords(), rows -> mapper.updateLots(rows, actorId));
        changed += writeBatches(buffer.policyRecords(), rows -> mapper.updatePolicies(rows, actorId));
        return changed;
    }

    @Override
    public int writeBatch(String runId, List<CanonicalInventoryRecord> records, Long actorId) {
        if (records == null || records.isEmpty()) return 0;
        requireActor(actorId);
        int changed = mapper.updateInventoryBalances(records, actorId);
        Long numericRunId = Long.valueOf(runId);
        Map<String, List<CanonicalInventoryRecord>> bySource = records.stream().collect(Collectors.groupingBy(CanonicalInventoryRecord::sourceType));
        bySource.forEach((sourceType, sourceRecords) -> {
            int marked = switch (sourceType) {
                case "OFFLINE" -> sourceWriteMapper.markOfflineSynced(sourceRecords, numericRunId);
                case "ECOMMERCE" -> sourceWriteMapper.markEcommerceSynced(sourceRecords, numericRunId);
                case "GREETING" -> sourceWriteMapper.markGreetingSynced(sourceRecords, numericRunId);
                case "WAREHOUSE" -> sourceWriteMapper.markWarehouseSynced(sourceRecords, numericRunId);
                default -> throw new IllegalArgumentException("unsupported source type: " + sourceType);
            };
            if (marked > 0) runSourceMapper.incrementChanged(numericRunId, sourceType, marked);
        });
        return changed;
    }

    private int writeBatches(Collection<CanonicalInventoryRecord> records,
                             ToIntFunction<List<CanonicalInventoryRecord>> writer) {
        if (records == null || records.isEmpty()) return 0;
        List<CanonicalInventoryRecord> values = List.copyOf(records);
        int changed = 0;
        for (int start = 0; start < values.size(); start += InventorySyncPublisher.BATCH_SIZE) {
            changed += writer.applyAsInt(values.subList(start, Math.min(start + InventorySyncPublisher.BATCH_SIZE, values.size())));
        }
        return changed;
    }

    private void requireActor(Long actorId) {
        if (actorId == null || actorId <= 0) {
            throw new IllegalStateException("inventory sync actor is required for canonical audit");
        }
    }

    @Override
    public int finish(String runId, Map<String, Long> sourceVersions, Set<String> riskScopes, Long actorId,
                      int changedCount) {
        Set<String> sourceTypes = sourceVersions.keySet();
        sourceWriteMapper.refreshState(sourceTypes, Long.valueOf(runId));
        sourceVersions.forEach((sourceType, version) -> runSourceMapper.completeSource(Long.valueOf(runId), sourceType, version, "SUCCESS"));
        var run = runMapper.selectByIdForUpdate(Long.valueOf(runId));
        if (run == null || !"RUNNING".equals(run.getRunStatus())
                || runMapper.updatePhase(Long.valueOf(runId), run.getMainAttemptNo(), run.getFencingToken(),
                        "ASSESSING_RISK", run.getReadCount(), run.getMappedCount()) != 1) {
            throw new IllegalStateException("STALE_FENCING");
        }
        Set<String> scopesToEvaluate = new LinkedHashSet<>();
        if (riskScopes != null) {
            scopesToEvaluate.addAll(riskScopes);
        }
        Set<String> outdatedRuleScopes = riskSnapshotLoader.findScopesRequiringRuleVersion(
                RiskRuleEngine.RULE_VERSION
        );
        int riskOnlyChangedCount = (int) outdatedRuleScopes.stream()
                .filter(scope -> !scopesToEvaluate.contains(scope))
                .count();
        scopesToEvaluate.addAll(outdatedRuleScopes);
        riskWriter.evaluateAndPersist(Long.valueOf(runId), actorId, scopesToEvaluate, riskSnapshotLoader);
        if (snapshotCoordinator != null && changedCount + riskOnlyChangedCount > 0) {
            snapshotCoordinator.scheduleAfterCommit(
                    Long.valueOf(runId),
                    LocalDate.now(ZoneId.of("Asia/Seoul"))
            );
        }
        return riskOnlyChangedCount;
    }
}
