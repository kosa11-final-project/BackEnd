package com.stockit.backend.feature.inventorysync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.explore.JobExplorer;

import com.stockit.backend.feature.inventorysync.mapper.InventorySyncRunMapper;
import com.stockit.backend.feature.inventorysync.service.InventorySyncBatchLauncher;
import com.stockit.backend.feature.inventorysync.service.InventorySyncRecoveryService;
import com.stockit.backend.feature.inventorysync.service.InventorySyncRunControlService;
import com.stockit.backend.feature.inventorysync.vo.InventorySyncRunVO;

class InventorySyncRecoveryServiceTest {
    private InventorySyncRunMapper runMapper;
    private InventorySyncBatchLauncher launcher;
    private InventorySyncRunControlService runControl;
    private JobExplorer jobExplorer;
    private InventorySyncRecoveryService service;

    @BeforeEach
    void setUp() {
        runMapper = Mockito.mock(InventorySyncRunMapper.class);
        launcher = Mockito.mock(InventorySyncBatchLauncher.class);
        runControl = Mockito.mock(InventorySyncRunControlService.class);
        jobExplorer = Mockito.mock(JobExplorer.class);
        service = new InventorySyncRecoveryService(runMapper, launcher, runControl, jobExplorer);
    }

    @Test
    void staleRunningRunIsInterruptedThenRedispatchedWithAFlushedAttempt() {
        Instant now = Instant.parse("2026-08-21T00:00:00Z");
        InventorySyncRunVO run = run("RUNNING", 7L, now.minusSeconds(301));
        when(runMapper.selectByIdForUpdate(10L)).thenReturn(run);
        when(runMapper.markInterrupted(10L, now)).thenReturn(1);
        when(runMapper.advanceRecoveryAttempt(10L, now)).thenReturn(1);
        when(launcher.launch(10L)).thenReturn(true);

        assertThat(service.recover(10L, "ops", 7L, now)).isTrue();

        verify(runMapper).markInterrupted(10L, now);
        verify(runMapper).advanceRecoveryAttempt(10L, now);
        verify(launcher).launch(10L);
        verify(runControl, never()).markLaunchFailed(anyLong(), any());
    }

    @Test
    void liveRunningOwnerCannotBeRecovered() {
        Instant now = Instant.parse("2026-08-21T00:00:00Z");
        InventorySyncRunVO run = run("RUNNING", 7L, now.minusSeconds(10));
        when(runMapper.selectByIdForUpdate(10L)).thenReturn(run);

        assertThat(service.recover(10L, "ops", 7L, now)).isFalse();

        verify(runMapper, never()).markInterrupted(10L, now);
        verify(runMapper, never()).advanceRecoveryAttempt(anyLong(), any());
        verify(launcher, never()).launch(anyLong());
    }

    @Test
    void launchRejectionIsRecordedByTheControlTransaction() {
        Instant now = Instant.parse("2026-08-21T00:00:00Z");
        InventorySyncRunVO run = run("INTERRUPTED", 7L, now.minusSeconds(301));
        when(runMapper.selectByIdForUpdate(10L)).thenReturn(run);
        when(runMapper.advanceRecoveryAttempt(10L, now)).thenReturn(1);
        when(launcher.launch(10L)).thenReturn(false);

        assertThat(service.recover(10L, "ops", 7L, now)).isTrue();

        verify(runControl).markLaunchFailed(10L, 1, 8L, "recovery worker queue가 가득 차 실행을 등록하지 못했습니다.");
    }

    @Test
    void queuedRecoveryDoesNotDuplicateAnExistingBatchExecution() {
        Instant now = Instant.parse("2026-08-21T00:00:00Z");
        InventorySyncRunVO run = run("QUEUED", 7L, now.minusSeconds(301));
        run.setMainBatchJobExecutionId(77L);
        when(runMapper.selectByIdForUpdate(10L)).thenReturn(run);
        when(jobExplorer.getJobExecution(77L)).thenReturn(new JobExecution(77L));

        assertThat(service.recover(10L, "ops", 7L, now)).isFalse();

        verify(runMapper, never()).advanceRecoveryAttempt(anyLong(), any());
        verify(launcher, never()).launch(anyLong());
    }

    @Test
    void queuedRecoveryAllowsACompletedFailureToStartAFreshFencedAttempt() {
        Instant now = Instant.parse("2026-08-21T00:00:00Z");
        InventorySyncRunVO run = run("QUEUED", 7L, now.minusSeconds(301));
        run.setMainBatchJobExecutionId(78L);
        when(runMapper.selectByIdForUpdate(10L)).thenReturn(run);
        var failedExecution = new JobExecution(78L);
        failedExecution.setStatus(BatchStatus.FAILED);
        when(jobExplorer.getJobExecution(78L)).thenReturn(failedExecution);
        when(runMapper.advanceRecoveryAttempt(10L, now)).thenReturn(1);
        when(launcher.launch(10L)).thenReturn(true);

        assertThat(service.recover(10L, "ops", 7L, now)).isTrue();

        verify(runMapper).advanceRecoveryAttempt(10L, now);
        verify(launcher).launch(10L);
    }

    private static InventorySyncRunVO run(String status, long fencingToken, Instant heartbeatAt) {
        InventorySyncRunVO run = new InventorySyncRunVO();
        run.setInventorySyncRunId(10L);
        run.setRunStatus(status);
        run.setFencingToken(fencingToken);
        run.setHeartbeatAt(heartbeatAt);
        run.setLeaseExpiresAt(heartbeatAt);
        run.setRequestedAt(heartbeatAt);
        return run;
    }
}
