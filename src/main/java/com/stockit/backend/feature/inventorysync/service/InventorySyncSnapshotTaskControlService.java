package com.stockit.backend.feature.inventorysync.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.stockit.backend.feature.inventorysync.mapper.InventorySyncSnapshotTaskMapper;
import com.stockit.backend.feature.inventorysync.vo.InventorySyncSnapshotTaskVO;

/** 스냅샷 본 트랜잭션과 분리해 claim 및 최종 상태를 짧게 확정합니다. */
@Service
public class InventorySyncSnapshotTaskControlService {
    private static final int MAX_PERSISTED_MESSAGE_CHARS = 500;

    private final InventorySyncSnapshotTaskMapper mapper;

    public InventorySyncSnapshotTaskControlService(InventorySyncSnapshotTaskMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    /** 호출 중인 canonical publish 트랜잭션에 참여하므로 rollback 시 작업도 함께 사라집니다. */
    @Transactional
    public void prepareTasks(Long runId, LocalDate businessDate) {
        mapper.insertPendingTasks(
                Objects.requireNonNull(runId, "runId"),
                Objects.requireNonNull(businessDate, "businessDate")
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claim(Long runId, String taskType, String owner, Instant leaseExpiresAt) {
        return mapper.claimTask(runId, taskType, owner, leaseExpiresAt) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markSucceeded(Long runId, String taskType, String owner) {
        return mapper.markSucceeded(runId, taskType, owner) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markRetryOrFailed(Long runId, String taskType, String owner, Instant nextAttemptAt,
                                     String errorCode, String errorMessage) {
        return mapper.markRetryOrFailed(runId, taskType, owner, nextAttemptAt,
                persistent(errorCode, 100), persistent(errorMessage, MAX_PERSISTED_MESSAGE_CHARS)) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markFailed(Long runId, String taskType, String owner,
                              String errorCode, String errorMessage) {
        return mapper.markFailed(runId, taskType, owner,
                persistent(errorCode, 100), persistent(errorMessage, MAX_PERSISTED_MESSAGE_CHARS)) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reconcilePersistedAndExpiredTasks() {
        // 먼저 실제 저장 행을 성공으로 복구해야 마지막 attempt의 lease 만료를 오판하지 않습니다.
        mapper.markPersistedSnapshotsSucceeded();
        mapper.markExpiredExhaustedTasksFailed();
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public List<InventorySyncSnapshotTaskVO> findRecoverableTasks(int limit) {
        if (limit <= 0) throw new IllegalArgumentException("limit must be positive");
        return List.copyOf(mapper.selectRecoverableTasks(limit));
    }

    private static String persistent(String value, int maxChars) {
        if (value == null) return null;
        return value.substring(0, Math.min(maxChars, value.length()));
    }
}
