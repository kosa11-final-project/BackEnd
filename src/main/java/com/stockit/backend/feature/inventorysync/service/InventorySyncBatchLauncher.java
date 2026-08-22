package com.stockit.backend.feature.inventorysync.service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
import com.stockit.backend.feature.inventorysync.mapper.InventorySyncRunMapper;

/** HTTP 연결과 분리된 bounded single-worker launcher입니다. */
@Component
public class InventorySyncBatchLauncher {

    private final ExecutorService executor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(4),
            runnable -> {
                Thread thread = new Thread(runnable, "inventory-sync-worker");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
    private final java.util.Set<String> inFlight = ConcurrentHashMap.newKeySet();
    private final JobLauncher jobLauncher;
    private final ObjectProvider<Job> inventorySyncMainJob;
    private final InventorySyncRunMapper runMapper;
    private final InventorySyncRunControlService runControl;

    public InventorySyncBatchLauncher(JobLauncher jobLauncher, @Qualifier("inventorySyncMainJob") ObjectProvider<Job> inventorySyncMainJob,
                                      InventorySyncRunMapper runMapper, InventorySyncRunControlService runControl) {
        this.jobLauncher = jobLauncher;
        this.inventorySyncMainJob = inventorySyncMainJob;
        this.runMapper = runMapper;
        this.runControl = runControl;
    }

    public boolean launch(Long runId) {
        if (runId == null || runId <= 0) throw new IllegalArgumentException("runId must be positive");
        var queuedRun = runMapper.selectById(runId);
        if (queuedRun == null) return false;
        return launch(runId, queuedRun.getMainAttemptNo(), queuedRun.getFencingToken());
    }

    private boolean launch(Long runId, int expectedAttemptNo, long expectedFencingToken) {
        String launchKey = runId + ":" + expectedAttemptNo + ":" + expectedFencingToken;
        // A duplicate callback for the same fenced attempt means that the
        // original launch is already registered, not that the run failed.
        if (!inFlight.add(launchKey)) return true;
        try {
            executor.submit(() -> {
                try {
                    var run = runMapper.selectById(runId);
                    if (run == null) return;
                    if (run.getMainAttemptNo() != expectedAttemptNo || run.getFencingToken() != expectedFencingToken
                            || !"QUEUED".equals(run.getRunStatus())) return;
                    Job batchJob = inventorySyncMainJob.getIfAvailable();
                    if (batchJob == null) {
                        runControl.markLaunchFailed(runId, expectedAttemptNo, expectedFencingToken,
                                "inventory sync Spring Batch job이 비활성화되어 실행할 수 없습니다.");
                        return;
                    }
                    JobExecution execution = jobLauncher.run(batchJob, new JobParametersBuilder()
                            .addLong("runId", runId, true)
                            .addLong("attemptNo", (long) run.getMainAttemptNo(), true)
                            .addLong("fencingToken", run.getFencingToken(), true)
                            .toJobParameters());
                    if (execution.getStatus() != BatchStatus.COMPLETED && !execution.getStatus().isRunning()) {
                        runControl.markLaunchFailed(runId, expectedAttemptNo, expectedFencingToken,
                                "Spring Batch 실행이 terminal 실패 상태로 종료되었습니다: " + execution.getStatus());
                    }
                } catch (Exception exception) {
                    runControl.markLaunchFailed(runId, expectedAttemptNo, expectedFencingToken,
                            "Spring Batch 실행 등록 실패: " + safeMessage(exception));
                } finally {
                    inFlight.remove(launchKey);
                }
            });
            return true;
        } catch (RejectedExecutionException exception) {
            inFlight.remove(launchKey);
            return false;
        }
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null ? exception.getClass().getSimpleName() : message.substring(0, Math.min(500, message.length()));
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
