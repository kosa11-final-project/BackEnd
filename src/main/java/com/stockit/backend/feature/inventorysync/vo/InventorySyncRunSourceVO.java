package com.stockit.backend.feature.inventorysync.vo;

public class InventorySyncRunSourceVO {
    private String sourceType;
    private String sourceStatus;
    private long readCount;
    private long mappedCount;
    private long changedCount;
    private long errorCount;
    public String getSourceType() { return sourceType; }
    public void setSourceType(String value) { sourceType = value; }
    public String getSourceStatus() { return sourceStatus; }
    public void setSourceStatus(String value) { sourceStatus = value; }
    public long getReadCount() { return readCount; }
    public void setReadCount(long value) { readCount = value; }
    public long getMappedCount() { return mappedCount; }
    public void setMappedCount(long value) { mappedCount = value; }
    public long getChangedCount() { return changedCount; }
    public void setChangedCount(long value) { changedCount = value; }
    public long getErrorCount() { return errorCount; }
    public void setErrorCount(long value) { errorCount = value; }
}
