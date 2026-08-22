package com.stockit.backend.feature.inventorysync.vo;

import java.time.Instant;

public class InventorySyncRunVO {
    private Long inventorySyncRunId;
    private String clientRequestId;
    private String requestHash;
    private String triggerType = "MANUAL";
    private String runStatus;
    private String currentPhase;
    private String activeScopeKey;
    private long fencingToken = 1;
    private Long mainBatchJobExecutionId;
    private int mainAttemptNo;
    private Long requestedBy;
    private Instant requestedAt;
    private Instant startedAt;
    private Instant heartbeatAt;
    private Instant leaseExpiresAt;
    private String leaseOwnerInstanceId;
    private Instant completedAt;
    private long readCount;
    private long mappedCount;
    private long changedCount;
    private long errorCount;
    private String errorCode;
    private String errorMessage;

    public Long getInventorySyncRunId() { return inventorySyncRunId; }
    public void setInventorySyncRunId(Long value) { inventorySyncRunId = value; }
    public String getClientRequestId() { return clientRequestId; }
    public void setClientRequestId(String value) { clientRequestId = value; }
    public String getRequestHash() { return requestHash; }
    public void setRequestHash(String value) { requestHash = value; }
    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String value) { triggerType = value; }
    public String getRunStatus() { return runStatus; }
    public void setRunStatus(String value) { runStatus = value; }
    public String getCurrentPhase() { return currentPhase; }
    public void setCurrentPhase(String value) { currentPhase = value; }
    public String getActiveScopeKey() { return activeScopeKey; }
    public void setActiveScopeKey(String value) { activeScopeKey = value; }
    public long getFencingToken() { return fencingToken; }
    public void setFencingToken(long value) { fencingToken = value; }
    public Long getMainBatchJobExecutionId() { return mainBatchJobExecutionId; }
    public void setMainBatchJobExecutionId(Long value) { mainBatchJobExecutionId = value; }
    public int getMainAttemptNo() { return mainAttemptNo; }
    public void setMainAttemptNo(int value) { mainAttemptNo = value; }
    public Long getRequestedBy() { return requestedBy; }
    public void setRequestedBy(Long value) { requestedBy = value; }
    public Instant getRequestedAt() { return requestedAt; }
    public void setRequestedAt(Instant value) { requestedAt = value; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant value) { startedAt = value; }
    public Instant getHeartbeatAt() { return heartbeatAt; }
    public void setHeartbeatAt(Instant value) { heartbeatAt = value; }
    public Instant getLeaseExpiresAt() { return leaseExpiresAt; }
    public void setLeaseExpiresAt(Instant value) { leaseExpiresAt = value; }
    public String getLeaseOwnerInstanceId() { return leaseOwnerInstanceId; }
    public void setLeaseOwnerInstanceId(String value) { leaseOwnerInstanceId = value; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant value) { completedAt = value; }
    public long getReadCount() { return readCount; }
    public void setReadCount(long value) { readCount = value; }
    public long getMappedCount() { return mappedCount; }
    public void setMappedCount(long value) { mappedCount = value; }
    public long getChangedCount() { return changedCount; }
    public void setChangedCount(long value) { changedCount = value; }
    public long getErrorCount() { return errorCount; }
    public void setErrorCount(long value) { errorCount = value; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String value) { errorCode = value; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String value) { errorMessage = value; }
}
