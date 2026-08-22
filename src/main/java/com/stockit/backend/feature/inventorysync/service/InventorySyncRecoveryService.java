package com.stockit.backend.feature.inventorysync.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.stockit.backend.feature.inventorysync.mapper.InventorySyncRunMapper;

/** 운영자 전용 recovery의 공통 검증 지점입니다. 실제 attempt 증가 명령은 U4 운영 CLI에서 호출합니다. */
@Service
public class InventorySyncRecoveryService {
    private final InventorySyncRunMapper runMapper;
    private final InventorySyncBatchLauncher launcher;
    private final InventorySyncRunControlService runControl;
    private final JobExplorer jobExplorer;

    @Autowired
    public InventorySyncRecoveryService(InventorySyncRunMapper runMapper, InventorySyncBatchLauncher launcher,
                                        InventorySyncRunControlService runControl, JobExplorer jobExplorer) {
        this.runMapper = runMapper;
        this.launcher = launcher;
        this.runControl = runControl;
        this.jobExplorer = jobExplorer;
    }

    /** 기존 단위 테스트/비-Batch wiring을 위한 명시적 no-explorer 생성자입니다. */
    public InventorySyncRecoveryService(InventorySyncRunMapper runMapper, InventorySyncBatchLauncher launcher,
                                        InventorySyncRunControlService runControl) {
        this(runMapper, launcher, runControl, null);
    }

    @Transactional
    public boolean recover(Long runId, String operator, Instant now) {
        if (runId == null) return false;
        var current = runMapper.selectById(runId);
        return current != null && recover(runId, operator, current.getFencingToken(), now);
    }

    /** 운영자가 확인한 fencing token과 현재 행을 함께 비교해 stale recovery 요청을 차단합니다. */
    @Transactional
    public boolean recover(Long runId, String operator, long expectedFencingToken, Instant now) {
        if (runId == null || operator == null || operator.isBlank() || now == null) return false;
        var run = runMapper.selectByIdForUpdate(runId);
        if (run == null) return false;
        if (run.getFencingToken() != expectedFencingToken) return false;
        if ("QUEUED".equals(run.getRunStatus()) && hasExistingBatchExecution(run)) return false;
        if ("RUNNING".equals(run.getRunStatus())) {
            if (run.getLeaseExpiresAt() != null && run.getLeaseExpiresAt().isAfter(now)) return false;
            if (run.getHeartbeatAt() != null && run.getHeartbeatAt().plusSeconds(300).isAfter(now)) return false;
            if (runMapper.markInterrupted(runId, now) != 1) return false;
            run.setRunStatus("INTERRUPTED");
        }
        if (!("INTERRUPTED".equals(run.getRunStatus()) || "QUEUED".equals(run.getRunStatus()))) return false;
        if ("QUEUED".equals(run.getRunStatus()) && (run.getRequestedAt() == null || run.getRequestedAt().plusSeconds(300).isAfter(now))) return false;
        if (run.getLeaseExpiresAt() != null && run.getLeaseExpiresAt().isAfter(now)) return false;
        if (run.getHeartbeatAt() != null && run.getHeartbeatAt().plusSeconds(300).isAfter(now)) return false;
        if (runMapper.advanceRecoveryAttempt(runId, now) != 1) return false;
        int nextAttemptNo = run.getMainAttemptNo() + 1;
        long nextFencingToken = run.getFencingToken() + 1;
        // 동일 run 재실행은 lease/owner가 stale인 경우에만 새 fencing token으로 허용합니다.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    if (!launcher.launch(runId)) {
                        runControl.markLaunchFailed(runId, nextAttemptNo, nextFencingToken,
                                "recovery worker queue가 가득 차 실행을 등록하지 못했습니다.");
                    }
                }
            });
        } else {
            if (!launcher.launch(runId)) {
                runControl.markLaunchFailed(runId, nextAttemptNo, nextFencingToken,
                        "recovery worker queue가 가득 차 실행을 등록하지 못했습니다.");
            }
        }
        return true;
    }

    private boolean hasExistingBatchExecution(com.stockit.backend.feature.inventorysync.vo.InventorySyncRunVO run) {
        if (jobExplorer == null) return false;
        JobExecution execution;
        if (run.getMainBatchJobExecutionId() != null) {
            execution = jobExplorer.getJobExecution(run.getMainBatchJobExecutionId());
            return execution != null && !isTerminal(execution);
        }
        var parameters = new JobParametersBuilder()
                .addLong("runId", run.getInventorySyncRunId(), true)
                .addLong("attemptNo", (long) run.getMainAttemptNo(), true)
                .addLong("fencingToken", run.getFencingToken(), true)
                .toJobParameters();
        var instance = jobExplorer.getJobInstance("inventorySyncMainJob", parameters);
        if (instance == null) return false;
        return jobExplorer.getJobExecutions(instance).stream().anyMatch(candidate -> !isTerminal(candidate));
    }

    private static boolean isTerminal(JobExecution execution) {
        BatchStatus status = execution.getStatus();
        return status == BatchStatus.COMPLETED
                || status == BatchStatus.FAILED
                || status == BatchStatus.STOPPED
                || status == BatchStatus.ABANDONED;
    }
}
