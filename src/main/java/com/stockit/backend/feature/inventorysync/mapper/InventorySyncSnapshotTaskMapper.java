package com.stockit.backend.feature.inventorysync.mapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.stockit.backend.feature.inventorysync.vo.InventorySyncSnapshotTaskVO;

@Mapper
public interface InventorySyncSnapshotTaskMapper {
    int insertPendingTasks(@Param("runId") Long runId, @Param("businessDate") LocalDate businessDate);

    List<InventorySyncSnapshotTaskVO> selectByRunId(@Param("runId") Long runId);

    List<InventorySyncSnapshotTaskVO> selectRecoverableTasks(@Param("limit") int limit);

    int claimTask(@Param("runId") Long runId,
                  @Param("taskType") String taskType,
                  @Param("owner") String owner,
                  @Param("leaseExpiresAt") Instant leaseExpiresAt);

    int markSucceeded(@Param("runId") Long runId,
                      @Param("taskType") String taskType,
                      @Param("owner") String owner);

    int markRetryOrFailed(@Param("runId") Long runId,
                          @Param("taskType") String taskType,
                          @Param("owner") String owner,
                          @Param("nextAttemptAt") Instant nextAttemptAt,
                          @Param("errorCode") String errorCode,
                          @Param("errorMessage") String errorMessage);

    int markFailed(@Param("runId") Long runId,
                   @Param("taskType") String taskType,
                   @Param("owner") String owner,
                   @Param("errorCode") String errorCode,
                   @Param("errorMessage") String errorMessage);

    int markPersistedSnapshotsSucceeded();

    int markExpiredExhaustedTasksFailed();
}
