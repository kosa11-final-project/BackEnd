package com.stockit.backend.feature.inventorysync.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.stockit.backend.feature.inventorysync.vo.InventorySyncRunVO;

@Mapper
public interface InventorySyncRunMapper {

    InventorySyncRunVO selectByClientRequestId(@Param("clientRequestId") String clientRequestId);

    InventorySyncRunVO selectById(@Param("runId") Long runId);
    InventorySyncRunVO selectByIdForUpdate(@Param("runId") Long runId);

    InventorySyncRunVO selectActiveRun();
    InventorySyncRunVO selectLatestRun();

    /** 동시 제출을 직렬화하는 고정 가드 행입니다. */
    int lockSubmissionGuard();

    int countRecentRequests(@Param("requestedBy") Long requestedBy, @Param("since") Instant since);
    Instant selectLastRequestedAt(@Param("requestedBy") Long requestedBy);
    Long selectSystemUserId();

    int insertRun(InventorySyncRunVO run);

    int markRunning(
            @Param("runId") Long runId,
            @Param("attemptNo") int attemptNo,
            @Param("fencingToken") long fencingToken,
            @Param("owner") String owner,
            @Param("leaseExpiresAt") Instant leaseExpiresAt
    );

    int updatePhase(
            @Param("runId") Long runId,
            @Param("attemptNo") int attemptNo,
            @Param("fencingToken") long fencingToken,
            @Param("phase") String phase,
            @Param("readCount") long readCount,
            @Param("mappedCount") long mappedCount
    );

    int heartbeat(
            @Param("runId") Long runId,
            @Param("attemptNo") int attemptNo,
            @Param("fencingToken") long fencingToken,
            @Param("owner") String owner,
            @Param("leaseExpiresAt") Instant leaseExpiresAt
    );

    int complete(
            @Param("runId") Long runId,
            @Param("attemptNo") int attemptNo,
            @Param("fencingToken") long fencingToken,
            @Param("status") String status,
            @Param("changedCount") long changedCount,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage
    );

    int updateError(@Param("runId") Long runId, @Param("errorCode") String errorCode, @Param("errorMessage") String errorMessage);

    List<String> selectSourceTypes();

    Long selectSourceVersion(@Param("sourceType") String sourceType);

    int assertWritable(@Param("runId") Long runId, @Param("attemptNo") int attemptNo, @Param("fencingToken") long fencingToken);

    int advanceRecoveryAttempt(@Param("runId") Long runId, @Param("now") Instant now);
    int markInterrupted(@Param("runId") Long runId, @Param("now") Instant now);
    int markLaunchFailed(@Param("runId") Long runId, @Param("message") String message);
    int setMainBatchExecutionId(@Param("runId") Long runId, @Param("executionId") Long executionId);
    int insertError(@Param("runId") Long runId, @Param("sourceType") String sourceType,
                    @Param("phase") String phase, @Param("errorCode") String errorCode,
                    @Param("errorMessage") String errorMessage);
}
