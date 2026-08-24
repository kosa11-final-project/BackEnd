package com.stockit.backend.feature.inventorysync.service;

import java.sql.SQLTransientException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.stockit.backend.feature.dashboard.service.DashboardSnapshotService;
import com.stockit.backend.feature.inventorysync.vo.InventorySyncSnapshotTaskVO;
import com.stockit.backend.feature.statistics.service.StatisticsSnapshotService;

/**
 * 재고 반영 커밋 이후 대시보드와 통계 스냅샷을 독립적으로 실행합니다.
 *
 * <p>실행 상태는 {@code inventory_sync_snapshot_task}에 먼저 기록됩니다. executor queue 포화,
 * 프로세스 재기동, 일시적 DB 오류가 생겨도 recovery scanner가 만료된 lease나 due task만
 * 다시 실행하며, 결정적 오류와 최대 시도 소진은 FAILED로 종료합니다.</p>
 */
@Component
public class InventorySyncSnapshotCoordinator {
    static final String DASHBOARD_TASK = "DASHBOARD";
    static final String INVENTORY_STATISTICS_TASK = "INVENTORY_STATISTICS";

    private static final Logger log = LoggerFactory.getLogger(InventorySyncSnapshotCoordinator.class);
    private static final int QUEUE_CAPACITY = 8;
    private static final Duration LEASE_DURATION = Duration.ofMinutes(10);
    private static final Duration RETRY_DELAY = Duration.ofSeconds(30);

    private final DashboardSnapshotService dashboardSnapshotService;
    private final StatisticsSnapshotService statisticsSnapshotService;
    private final InventorySyncSnapshotTaskControlService taskControlService;
    private final ExecutorService dashboardExecutor;
    private final ExecutorService statisticsExecutor;
    private final String instanceId;
    private final Set<String> inFlightTasks = ConcurrentHashMap.newKeySet();

    @Autowired
    public InventorySyncSnapshotCoordinator(
            DashboardSnapshotService dashboardSnapshotService,
            StatisticsSnapshotService statisticsSnapshotService,
            InventorySyncSnapshotTaskControlService taskControlService,
            @Value("${app.inventory-sync.instance-id:local-worker}") String instanceId
    ) {
        this(
                dashboardSnapshotService,
                statisticsSnapshotService,
                taskControlService,
                newExecutor("inventory-dashboard-snapshot"),
                newExecutor("inventory-statistics-snapshot"),
                instanceId
        );
    }

    InventorySyncSnapshotCoordinator(
            DashboardSnapshotService dashboardSnapshotService,
            StatisticsSnapshotService statisticsSnapshotService,
            InventorySyncSnapshotTaskControlService taskControlService,
            ExecutorService dashboardExecutor,
            ExecutorService statisticsExecutor,
            String instanceId
    ) {
        this.dashboardSnapshotService = Objects.requireNonNull(dashboardSnapshotService, "dashboardSnapshotService");
        this.statisticsSnapshotService = Objects.requireNonNull(statisticsSnapshotService, "statisticsSnapshotService");
        this.taskControlService = Objects.requireNonNull(taskControlService, "taskControlService");
        this.dashboardExecutor = Objects.requireNonNull(dashboardExecutor, "dashboardExecutor");
        this.statisticsExecutor = Objects.requireNonNull(statisticsExecutor, "statisticsExecutor");
        this.instanceId = Objects.requireNonNull(instanceId, "instanceId");
    }

    /** canonical publish 트랜잭션에 작업 행을 함께 저장하고, 커밋된 뒤에만 executor에 넣습니다. */
    public void scheduleAfterCommit(Long syncRunId, LocalDate businessDate) {
        Objects.requireNonNull(syncRunId, "syncRunId must not be null");
        Objects.requireNonNull(businessDate, "businessDate must not be null");
        taskControlService.prepareTasks(syncRunId, businessDate);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    schedule(syncRunId, businessDate);
                }
            });
            return;
        }
        schedule(syncRunId, businessDate);
    }

    /** recovery scanner가 이미 저장된 작업을 executor에 다시 넣을 때 사용합니다. */
    public void scheduleTask(InventorySyncSnapshotTaskVO task) {
        Objects.requireNonNull(task, "task");
        scheduleTask(task.getInventorySyncRunId(), task.getTaskType(), task.getBusinessDate());
    }

    void schedule(Long syncRunId, LocalDate businessDate) {
        scheduleTask(syncRunId, DASHBOARD_TASK, businessDate);
        scheduleTask(syncRunId, INVENTORY_STATISTICS_TASK, businessDate);
    }

    private void scheduleTask(Long syncRunId, String taskType, LocalDate businessDate) {
        Objects.requireNonNull(syncRunId, "syncRunId must not be null");
        Objects.requireNonNull(businessDate, "businessDate must not be null");
        switch (taskType) {
            case DASHBOARD_TASK -> submit(
                    dashboardExecutor,
                    () -> dashboardSnapshotService.createSnapshot(syncRunId, businessDate),
                    taskType,
                    syncRunId
            );
            case INVENTORY_STATISTICS_TASK -> submit(
                    statisticsExecutor,
                    () -> statisticsSnapshotService.createInventorySnapshots(syncRunId, businessDate),
                    taskType,
                    syncRunId
            );
            default -> log.error("unsupported inventory snapshot task: runId={}, taskType={}", syncRunId, taskType);
        }
    }

    private void submit(ExecutorService executor, Runnable task, String taskType, Long syncRunId) {
        String taskKey = taskType + ':' + syncRunId;
        if (!inFlightTasks.add(taskKey)) {
            log.debug("inventory snapshot is already queued: runId={}, taskType={}", syncRunId, taskType);
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    executeOneAttempt(task, taskType, syncRunId);
                } finally {
                    inFlightTasks.remove(taskKey);
                }
            });
        } catch (RejectedExecutionException exception) {
            inFlightTasks.remove(taskKey);
            log.error("inventory snapshot queue is full: runId={}, taskType={}", syncRunId, taskType, exception);
        }
    }

    private void executeOneAttempt(Runnable task, String taskType, Long syncRunId) {
        String owner = taskOwner();
        if (!taskControlService.claim(syncRunId, taskType, owner, Instant.now().plus(LEASE_DURATION))) {
            log.debug("inventory snapshot claim skipped: runId={}, taskType={}", syncRunId, taskType);
            return;
        }

        try {
            task.run();
            if (!taskControlService.markSucceeded(syncRunId, taskType, owner)) {
                log.warn("inventory snapshot succeeded but task ownership changed: runId={}, taskType={}",
                        syncRunId, taskType);
                return;
            }
            log.info("inventory snapshot synchronized: runId={}, taskType={}", syncRunId, taskType);
        } catch (Exception exception) {
            boolean retryable = isRetryable(exception);
            boolean updated = retryable
                    ? taskControlService.markRetryOrFailed(
                            syncRunId, taskType, owner, Instant.now().plus(RETRY_DELAY),
                            "SNAPSHOT_TRANSIENT_FAILURE", failureMessage(exception)
                    )
                    : taskControlService.markFailed(
                            syncRunId, taskType, owner,
                            "SNAPSHOT_DETERMINISTIC_FAILURE", failureMessage(exception)
                    );
            log.error("inventory snapshot attempt failed: runId={}, taskType={}, retryable={}, stateUpdated={}",
                    syncRunId, taskType, retryable, updated, exception);
        }
    }

    private boolean isRetryable(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof TransientDataAccessException
                    || cause instanceof RecoverableDataAccessException
                    || cause instanceof DataAccessResourceFailureException
                    || cause instanceof SQLTransientException) {
                return true;
            }
        }
        return false;
    }

    private String taskOwner() {
        String base = instanceId.substring(0, Math.min(140, instanceId.length()));
        return base + ":snapshot:" + UUID.randomUUID();
    }

    private static String failureMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getName() : message;
    }

    @PreDestroy
    void shutdown() {
        dashboardExecutor.shutdownNow();
        statisticsExecutor.shutdownNow();
    }

    private static ExecutorService newExecutor(String threadName) {
        return new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                runnable -> {
                    Thread thread = new Thread(runnable, threadName);
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
    }
}
