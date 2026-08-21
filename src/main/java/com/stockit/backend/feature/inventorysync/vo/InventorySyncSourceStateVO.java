package com.stockit.backend.feature.inventorysync.vo;

import java.time.Instant;

public class InventorySyncSourceStateVO {
    private String sourceType;
    private long currentVersion;
    private long currentRecordCount;
    private long pendingRecordCount;
    private Instant lastChangedAt;
    private Instant lastSuccessSyncedAt;
    public String getSourceType() { return sourceType; }
    public void setSourceType(String value) { sourceType = value; }
    public long getCurrentVersion() { return currentVersion; }
    public void setCurrentVersion(long value) { currentVersion = value; }
    public long getCurrentRecordCount() { return currentRecordCount; }
    public void setCurrentRecordCount(long value) { currentRecordCount = value; }
    public long getPendingRecordCount() { return pendingRecordCount; }
    public void setPendingRecordCount(long value) { pendingRecordCount = value; }
    public Instant getLastChangedAt() { return lastChangedAt; }
    public void setLastChangedAt(Instant value) { lastChangedAt = value; }
    public Instant getLastSuccessSyncedAt() { return lastSuccessSyncedAt; }
    public void setLastSuccessSyncedAt(Instant value) { lastSuccessSyncedAt = value; }
}
