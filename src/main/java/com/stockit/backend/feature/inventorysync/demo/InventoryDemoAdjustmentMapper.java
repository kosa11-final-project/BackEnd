package com.stockit.backend.feature.inventorysync.demo;

import java.math.BigDecimal;
import java.time.Instant;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface InventoryDemoAdjustmentMapper {
    DemoAuditRow selectByRequestId(@Param("requestId") String requestId);
    int countRecentApplied(@Param("requestedBy") Long requestedBy, @Param("since") Instant since);
    Instant selectLastAppliedAt(@Param("requestedBy") Long requestedBy);
    int lockSourceState(@Param("sourceType") String sourceType);
    DemoSourceRow lockSourceRow(@Param("sourceType") String sourceType, @Param("sourceRecordKey") String sourceRecordKey);
    int updateSource(@Param("sourceType") String sourceType, @Param("sourceRecordKey") String sourceRecordKey,
                     @Param("decreaseQty") BigDecimal decreaseQty, @Param("sourceHashAfter") String sourceHashAfter);
    int updatePendingCount(@Param("sourceType") String sourceType, @Param("wasSynced") int wasSynced);
    int insertAudit(@Param("requestId") String requestId, @Param("requestHash") String requestHash,
                    @Param("sourceType") String sourceType, @Param("sourceRecordKey") String sourceRecordKey,
                    @Param("decreaseQty") BigDecimal decreaseQty, @Param("sourceRowVersionBefore") long sourceRowVersionBefore,
                    @Param("sourceRowVersionAfter") long sourceRowVersionAfter, @Param("sourceHashBefore") String sourceHashBefore,
                    @Param("sourceHashAfter") String sourceHashAfter, @Param("requestedBy") Long requestedBy,
                    @Param("payloadJson") String payloadJson);
    BulkSourceStateRow lockBulkSourceState(@Param("sourceType") String sourceType);
    int countAdjustableSyncedRows(@Param("sourceType") String sourceType);
    int insertBulkAudit(@Param("requestId") String requestId, @Param("requestHash") String requestHash,
                        @Param("sourceType") String sourceType, @Param("decreaseQty") BigDecimal decreaseQty,
                        @Param("requestedBy") Long requestedBy);
    int updateAllSyncedSources(@Param("sourceType") String sourceType,
                               @Param("decreaseQty") BigDecimal decreaseQty);
    int updatePendingCountBulk(@Param("sourceType") String sourceType,
                               @Param("adjustedCount") int adjustedCount);

    class DemoSourceRow {
        private BigDecimal onHandQty;
        private BigDecimal reservedQty;
        private long rowVersion;
        private String recordHash;
        private String syncedRecordHash;
        public BigDecimal getOnHandQty() { return onHandQty; }
        public void setOnHandQty(BigDecimal value) { onHandQty = value; }
        public BigDecimal getReservedQty() { return reservedQty; }
        public void setReservedQty(BigDecimal value) { reservedQty = value; }
        public long getRowVersion() { return rowVersion; }
        public void setRowVersion(long value) { rowVersion = value; }
        public String getRecordHash() { return recordHash; }
        public void setRecordHash(String value) { recordHash = value; }
        public String getSyncedRecordHash() { return syncedRecordHash; }
        public void setSyncedRecordHash(String value) { syncedRecordHash = value; }
    }

    /** MyBatis가 audit 한 행을 안전하게 복원하는 idempotency 조회 결과입니다. */
    record DemoAuditRow(String requestId, String requestHash, String status, int appliedCount, Instant appliedAt) { }
    record BulkSourceStateRow(long currentRecordCount, long pendingRecordCount) { }
}
