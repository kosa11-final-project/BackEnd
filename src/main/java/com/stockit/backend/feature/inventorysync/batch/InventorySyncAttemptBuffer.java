package com.stockit.backend.feature.inventorysync.batch;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;

import com.stockit.backend.feature.inventorysync.adapter.CanonicalInventoryRecord;

/**
 * 한 Batch attempt에서만 사용하는 bounded changed-set buffer입니다.
 * Spring Batch ExecutionContext에는 이 객체를 넣지 않습니다. 재시작은 page 1부터
 * 새 attempt가 buffer를 재구성해야 하므로 stale buffer가 publish되는 것을 막습니다.
 */
public final class InventorySyncAttemptBuffer {

    public static final int DEFAULT_MAX_RECORDS = 100_000;

    private final String runId;
    private final int attemptNo;
    private final long fencingToken;
    private final Long requestedBy;
    private final int maxRecords;
    private final Map<Long, CanonicalInventoryRecord> records = new LinkedHashMap<>();
    private final Map<Long, CanonicalInventoryRecord> productRecords = new LinkedHashMap<>();
    private final Map<Long, CanonicalInventoryRecord> skuRecords = new LinkedHashMap<>();
    private final Map<Long, CanonicalInventoryRecord> priceRecords = new LinkedHashMap<>();
    private final Map<Long, CanonicalInventoryRecord> skuCostRecords = new LinkedHashMap<>();
    private final Map<Long, CanonicalInventoryRecord> lotRecords = new LinkedHashMap<>();
    private final Map<Long, CanonicalInventoryRecord> policyRecords = new LinkedHashMap<>();
    private final Map<String, Long> sourceVersions = new LinkedHashMap<>();
    private final Set<String> riskScopes = new LinkedHashSet<>();

    public InventorySyncAttemptBuffer(String runId, int attemptNo, long fencingToken) {
        this(runId, attemptNo, fencingToken, DEFAULT_MAX_RECORDS);
    }

    public InventorySyncAttemptBuffer(String runId, int attemptNo, long fencingToken, int maxRecords) {
        this(runId, attemptNo, fencingToken, maxRecords, null);
    }

    public InventorySyncAttemptBuffer(String runId, int attemptNo, long fencingToken, int maxRecords, Long requestedBy) {
        if (runId == null || runId.isBlank() || attemptNo < 0 || fencingToken <= 0 || maxRecords < 1) {
            throw new IllegalArgumentException("invalid sync attempt identity or capacity");
        }
        this.runId = runId;
        this.attemptNo = attemptNo;
        this.fencingToken = fencingToken;
        this.maxRecords = maxRecords;
        this.requestedBy = requestedBy;
    }

    public void recordSourceVersion(String sourceType, long version) {
        if (sourceType == null || sourceType.isBlank() || version <= 0) {
            throw new IllegalArgumentException("sourceType/version is invalid");
        }
        sourceVersions.put(sourceType, version);
    }

    /** 같은 canonical PK가 여러 source row에 의해 참조되면 authority conflict로 fail-close합니다. */
    public void add(CanonicalInventoryRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("record must not be null");
        }
        CanonicalInventoryRecord previous = records.get(record.inventoryBalanceId());
        if (previous != null) {
            if (!previous.equals(record)) {
                throw new IllegalStateException("duplicate canonical target with different values: "
                        + record.inventoryBalanceId());
            }
            return;
        }
        if (records.size() >= maxRecords) {
            throw new IllegalStateException("inventory sync changed-set exceeds " + maxRecords + " records");
        }
        records.put(record.inventoryBalanceId(), record);
        addTarget(productRecords, record.productId(), record, "PRODUCT", this::sameProduct);
        addTarget(skuRecords, record.skuId(), record, "SKU", this::sameSku);
        addTarget(priceRecords, record.skuChannelPriceId(), record, "SKU_CHANNEL_PRICE", this::samePrice);
        addTarget(skuCostRecords, record.skuCostId(), record, "SKU_COST", this::sameSkuCost);
        addLot(record);
        addTarget(policyRecords, record.inventoryPolicyId(), record, "INVENTORY_POLICY", this::samePolicy);
        riskScopes.add(record.riskScopeKey());
    }

    public void addAll(Collection<CanonicalInventoryRecord> values) {
        if (values == null) {
            throw new IllegalArgumentException("values must not be null");
        }
        values.forEach(this::add);
    }

    public String runId() { return runId; }
    public int attemptNo() { return attemptNo; }
    public long fencingToken() { return fencingToken; }
    public Long requestedBy() { return requestedBy; }
    public int size() { return records.size(); }
    public Collection<CanonicalInventoryRecord> records() { return Collections.unmodifiableCollection(records.values()); }
    public Collection<CanonicalInventoryRecord> productRecords() { return Collections.unmodifiableCollection(productRecords.values()); }
    public Collection<CanonicalInventoryRecord> skuRecords() { return Collections.unmodifiableCollection(skuRecords.values()); }
    public Collection<CanonicalInventoryRecord> priceRecords() { return Collections.unmodifiableCollection(priceRecords.values()); }
    public Collection<CanonicalInventoryRecord> skuCostRecords() { return Collections.unmodifiableCollection(skuCostRecords.values()); }
    public Collection<CanonicalInventoryRecord> lotRecords() { return Collections.unmodifiableCollection(lotRecords.values()); }
    public Collection<CanonicalInventoryRecord> policyRecords() { return Collections.unmodifiableCollection(policyRecords.values()); }
    public Map<Long, CanonicalInventoryRecord> recordsByBalanceId() { return Collections.unmodifiableMap(records); }
    public Map<String, Long> sourceVersions() { return Collections.unmodifiableMap(sourceVersions); }
    public Set<String> riskScopes() { return Collections.unmodifiableSet(riskScopes); }

    private void addTarget(Map<Long, CanonicalInventoryRecord> targets, Long targetId,
                           CanonicalInventoryRecord record, String targetName,
                           BiPredicate<CanonicalInventoryRecord, CanonicalInventoryRecord> compatible) {
        if (targetId == null) return;
        CanonicalInventoryRecord previous = targets.putIfAbsent(targetId, record);
        if (previous != null && !compatible.test(previous, record)) {
            throw new IllegalStateException("conflicting " + targetName + " source values for target " + targetId);
        }
    }

    private void addLot(CanonicalInventoryRecord record) {
        CanonicalInventoryRecord previous = lotRecords.get(record.lotId());
        if (previous == null) {
            lotRecords.put(record.lotId(), record);
            return;
        }
        // LOT의 물리 날짜는 창고 원천이 우선합니다. 상태는 원천 값을 신뢰하지 않고
        // publish 마지막에 날짜와 통합 잔량으로 다시 계산합니다.
        if ("WAREHOUSE".equals(record.sourceType())) {
            lotRecords.put(record.lotId(), record);
            return;
        }
        if (!"WAREHOUSE".equals(previous.sourceType()) && !sameLot(previous, record)) {
            throw new IllegalStateException("conflicting LOT source values for target " + record.lotId());
        }
    }

    private boolean sameProduct(CanonicalInventoryRecord left, CanonicalInventoryRecord right) {
        return Objects.equals(left.categoryId(), right.categoryId())
                && Objects.equals(left.productName(), right.productName())
                && compatibleNullable(left.brandName(), right.brandName())
                && Objects.equals(left.productStatus(), right.productStatus())
                && compatibleNullable(left.saleAvailableYn(), right.saleAvailableYn());
    }

    private boolean sameSku(CanonicalInventoryRecord left, CanonicalInventoryRecord right) {
        return Objects.equals(left.skuName(), right.skuName())
                && Objects.equals(left.netWeight(), right.netWeight())
                && Objects.equals(left.weightUnit(), right.weightUnit())
                && Objects.equals(left.packageQuantity(), right.packageQuantity())
                && Objects.equals(left.unitCode(), right.unitCode())
                && Objects.equals(left.storageType(), right.storageType());
    }

    private boolean samePrice(CanonicalInventoryRecord left, CanonicalInventoryRecord right) {
        return Objects.equals(left.sellingPrice(), right.sellingPrice())
                && Objects.equals(left.actualPrice(), right.actualPrice())
                && Objects.equals(left.productCost(), right.productCost())
                && Objects.equals(left.paymentFee(), right.paymentFee())
                && Objects.equals(left.logisticsCost(), right.logisticsCost())
                && Objects.equals(left.priceEffectiveFrom(), right.priceEffectiveFrom())
                && Objects.equals(left.priceEffectiveTo(), right.priceEffectiveTo());
    }

    private boolean sameSkuCost(CanonicalInventoryRecord left, CanonicalInventoryRecord right) {
        return Objects.equals(left.skuUnitCost(), right.skuUnitCost());
    }

    private boolean sameLot(CanonicalInventoryRecord left, CanonicalInventoryRecord right) {
        return Objects.equals(left.manufacturedDate(), right.manufacturedDate())
                && Objects.equals(left.receivedDate(), right.receivedDate())
                && Objects.equals(left.expiryDate(), right.expiryDate())
                && Objects.equals(left.saleStopDate(), right.saleStopDate());
    }

    private boolean samePolicy(CanonicalInventoryRecord left, CanonicalInventoryRecord right) {
        return Objects.equals(left.safetyStockQty(), right.safetyStockQty())
                && Objects.equals(left.targetStockQty(), right.targetStockQty());
    }

    private boolean compatibleNullable(Object left, Object right) {
        return left == null || right == null || Objects.equals(left, right);
    }
}
