package com.stockit.backend.feature.inventorysync.vo;

import java.time.Instant;
import java.time.LocalDate;

/** 재고 동기화 뒤 실행되는 영속 스냅샷 작업입니다. */
public class InventorySyncSnapshotTaskVO {
    private Long inventorySyncSnapshotTaskId;
    private Long inventorySyncRunId;
    private String taskType;
    private String taskStatus;
    private LocalDate businessDate;
    private int attemptCount;
    private int maxAttempts;
    private Instant nextAttemptAt;
    private String leaseOwnerInstanceId;
    private Instant leaseExpiresAt;

    public Long getInventorySyncSnapshotTaskId() { return inventorySyncSnapshotTaskId; }
    public void setInventorySyncSnapshotTaskId(Long value) { this.inventorySyncSnapshotTaskId = value; }
    public Long getInventorySyncRunId() { return inventorySyncRunId; }
    public void setInventorySyncRunId(Long value) { this.inventorySyncRunId = value; }
    public String getTaskType() { return taskType; }
    public void setTaskType(String value) { this.taskType = value; }
    public String getTaskStatus() { return taskStatus; }
    public void setTaskStatus(String value) { this.taskStatus = value; }
    public LocalDate getBusinessDate() { return businessDate; }
    public void setBusinessDate(LocalDate value) { this.businessDate = value; }
    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int value) { this.attemptCount = value; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int value) { this.maxAttempts = value; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(Instant value) { this.nextAttemptAt = value; }
    public String getLeaseOwnerInstanceId() { return leaseOwnerInstanceId; }
    public void setLeaseOwnerInstanceId(String value) { this.leaseOwnerInstanceId = value; }
    public Instant getLeaseExpiresAt() { return leaseExpiresAt; }
    public void setLeaseExpiresAt(Instant value) { this.leaseExpiresAt = value; }
}
