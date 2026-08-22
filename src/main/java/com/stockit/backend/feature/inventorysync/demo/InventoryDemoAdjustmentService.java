package com.stockit.backend.feature.inventorysync.demo;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.inventorysync.InventorySyncSourceOrder;
import com.stockit.backend.feature.inventorysync.InventorySyncHash;

@Service
public class InventoryDemoAdjustmentService {
    private static final int MAX_PER_HOUR = 60;
    private final InventoryDemoAdjustmentMapper mapper;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public InventoryDemoAdjustmentService(
            InventoryDemoAdjustmentMapper mapper,
            ObjectMapper objectMapper,
            @Value("${app.inventory-sync.demo-enabled:false}") boolean enabled
    ) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    @Transactional
    public InventoryDemoAdjustmentResponse apply(InventoryDemoAdjustmentRequest request, Long requestedBy) {
        if (!enabled) throw new IllegalStateException("demo adjustment is disabled");
        if (requestedBy == null || requestedBy <= 0) throw new AppException(ErrorCode.AUTHENTICATION_FAILED);
        String hash = requestHash(request);
        InventoryDemoAdjustmentMapper.DemoAuditRow existing = mapper.selectByRequestId(request.clientRequestId());
        if (existing != null) {
            if (!hash.equals(existing.requestHash())) throw new AppException(ErrorCode.INVENTORY_SYNC_CONFLICT, "같은 clientRequestId에 다른 payload가 사용되었습니다.");
            // audit은 요청 중복 실행을 막는 권위 데이터이며, 원래 item 결과를 재구성하지 못하는 경우에도
            // source를 다시 차감하지 않고 적용 건수만 반환한다.
            return new InventoryDemoAdjustmentResponse(existing.requestId(), existing.status(), existing.appliedCount(), existing.appliedAt(), List.of());
        }
        Instant lastAppliedAt = mapper.selectLastAppliedAt(requestedBy);
        if (lastAppliedAt != null && lastAppliedAt.plusSeconds(10).isAfter(Instant.now())) {
            throw new DemoRateLimitException((int) Math.max(1, lastAppliedAt.plusSeconds(10).getEpochSecond() - Instant.now().getEpochSecond()));
        }
        if (mapper.countRecentApplied(requestedBy, Instant.now().minus(Duration.ofHours(1))) >= MAX_PER_HOUR) {
            throw new DemoRateLimitException(10);
        }
        validateUnique(request.items());
        List<InventoryDemoAdjustmentRequest.Item> items = request.items().stream()
                .sorted(Comparator.comparing(InventoryDemoAdjustmentRequest.Item::sourceType, InventorySyncSourceOrder.COMPARATOR).thenComparing(InventoryDemoAdjustmentRequest.Item::sourceRecordKey))
                .toList();
        List<InventoryDemoAdjustmentResponse.ItemResult> results = new ArrayList<>();
        for (InventoryDemoAdjustmentRequest.Item item : items) {
            if (mapper.lockSourceState(item.sourceType()) != 1) {
                throw new AppException(ErrorCode.INVENTORY_SYNC_CONFLICT, "원천 상태가 초기화되지 않았습니다: " + item.sourceType());
            }
            InventoryDemoAdjustmentMapper.DemoSourceRow source = mapper.lockSourceRow(item.sourceType(), item.sourceRecordKey());
            if (source == null || source.getOnHandQty() == null || source.getOnHandQty().compareTo(item.decreaseQty()) < 0) {
                throw new AppException(ErrorCode.INVALID_PARAMETER, "차감 수량이 현재 원천 가용재고를 초과합니다: " + item.sourceRecordKey());
            }
            BigDecimal remaining = source.getOnHandQty().subtract(item.decreaseQty());
            String hashAfter = hashAfter(item.sourceRecordKey(), remaining, source.getRowVersion() + 1);
            if (mapper.updateSource(item.sourceType(), item.sourceRecordKey(), item.decreaseQty(), hashAfter) != 1) {
                throw new AppException(ErrorCode.INVENTORY_SYNC_CONFLICT, "원천 재고가 동시에 변경되었습니다: " + item.sourceRecordKey());
            }
            int wasSynced = source.getRecordHash() != null && source.getRecordHash().equals(source.getSyncedRecordHash()) ? 1 : 0;
            mapper.updatePendingCount(item.sourceType(), wasSynced);
            mapper.insertAudit(request.clientRequestId(), hash, item.sourceType(), item.sourceRecordKey(), item.decreaseQty(),
                    source.getRowVersion(), source.getRowVersion() + 1, source.getRecordHash(), hashAfter, requestedBy,
                    payload(item));
            results.add(new InventoryDemoAdjustmentResponse.ItemResult(item.sourceType(), item.sourceRecordKey(), item.decreaseQty(), remaining));
        }
        return new InventoryDemoAdjustmentResponse(request.clientRequestId(), "APPLIED", results.size(), Instant.now(), results);
    }

    private void validateUnique(List<InventoryDemoAdjustmentRequest.Item> items) {
        Set<String> keys = new HashSet<>();
        for (var item : items) {
            if (!keys.add(item.sourceType() + "\u0000" + item.sourceRecordKey())) throw new AppException(ErrorCode.INVALID_PARAMETER, "같은 원천 행이 요청에 중복되었습니다.");
            if (item.decreaseQty().scale() > 3 || item.decreaseQty().compareTo(new BigDecimal("999999999999.999")) > 0) throw new AppException(ErrorCode.INVALID_PARAMETER, "decreaseQty 범위를 벗어났습니다.");
        }
    }

    private String payload(InventoryDemoAdjustmentRequest.Item item) {
        try { return objectMapper.writeValueAsString(java.util.Map.of("sourceType", item.sourceType(), "sourceRecordKey", item.sourceRecordKey(), "decreaseQty", item.decreaseQty())); }
        catch (Exception exception) { throw new IllegalStateException("audit payload serialization failed", exception); }
    }

    public static String requestHash(InventoryDemoAdjustmentRequest request) {
        String canonical = request.clientRequestId() + "|" + request.items().stream().sorted(Comparator.comparing(InventoryDemoAdjustmentRequest.Item::sourceType, InventorySyncSourceOrder.COMPARATOR).thenComparing(InventoryDemoAdjustmentRequest.Item::sourceRecordKey)).map(item -> item.sourceType() + "|" + item.sourceRecordKey() + "|" + item.decreaseQty().stripTrailingZeros().toPlainString()).reduce("", (left, right) -> left + right + "|");
        return InventorySyncHash.sha256Hex(canonical);
    }

    private static String hashAfter(String key, BigDecimal remaining, long rowVersion) {
        return InventorySyncHash.sha256Hex(key + "|" + remaining.stripTrailingZeros().toPlainString() + "|" + rowVersion);
    }

    public static class DemoRateLimitException extends RuntimeException {
        private final int retryAfterSeconds;
        public DemoRateLimitException(int retryAfterSeconds) { this.retryAfterSeconds = retryAfterSeconds; }
        public int retryAfterSeconds() { return retryAfterSeconds; }
    }
}
