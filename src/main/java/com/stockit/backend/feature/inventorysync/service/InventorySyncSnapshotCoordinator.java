package com.stockit.backend.feature.inventorysync.service;

import java.time.LocalDate;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.stockit.backend.feature.dashboard.service.DashboardSnapshotService;
import com.stockit.backend.feature.statistics.service.StatisticsSnapshotService;

/**
 * 재고 반영 커밋 이후 대시보드와 통계 스냅샷을 독립적으로 예약합니다.
 *
 * <p>두 작업은 서로 다른 executor와 트랜잭션에서 실행됩니다. 한쪽 API가 실패하거나
 * worker queue가 포화되어도 다른 작업과 이미 완료된 재고 동기화에는 영향을 주지 않습니다.</p>
 */
@Component
public class InventorySyncSnapshotCoordinator {
    private static final Logger log = LoggerFactory.getLogger(InventorySyncSnapshotCoordinator.class);
    private static final int QUEUE_CAPACITY = 8;

    private final DashboardSnapshotService dashboardSnapshotService;
    private final StatisticsSnapshotService statisticsSnapshotService;
    private final ExecutorService dashboardExecutor;
    private final ExecutorService statisticsExecutor;

    @Autowired
    public InventorySyncSnapshotCoordinator(
            DashboardSnapshotService dashboardSnapshotService,
            StatisticsSnapshotService statisticsSnapshotService
    ) {
        this(
                dashboardSnapshotService,
                statisticsSnapshotService,
                newExecutor("inventory-dashboard-snapshot"),
                newExecutor("inventory-statistics-snapshot")
        );
    }

    InventorySyncSnapshotCoordinator(
            DashboardSnapshotService dashboardSnapshotService,
            StatisticsSnapshotService statisticsSnapshotService,
            ExecutorService dashboardExecutor,
            ExecutorService statisticsExecutor
    ) {
        this.dashboardSnapshotService = Objects.requireNonNull(dashboardSnapshotService, "dashboardSnapshotService");
        this.statisticsSnapshotService = Objects.requireNonNull(statisticsSnapshotService, "statisticsSnapshotService");
        this.dashboardExecutor = Objects.requireNonNull(dashboardExecutor, "dashboardExecutor");
        this.statisticsExecutor = Objects.requireNonNull(statisticsExecutor, "statisticsExecutor");
    }

    /**
     * 현재 트랜잭션이 커밋된 뒤 두 스냅샷 작업을 각각 예약합니다.
     * 롤백된 재고 상태를 스냅샷으로 읽지 않도록 afterCommit을 사용합니다.
     */
    public void scheduleAfterCommit(Long syncRunId, LocalDate businessDate) {
        Objects.requireNonNull(syncRunId, "syncRunId must not be null");
        Objects.requireNonNull(businessDate, "businessDate must not be null");

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

    void schedule(Long syncRunId, LocalDate businessDate) {
        submit(
                dashboardExecutor,
                () -> dashboardSnapshotService.createSnapshot(syncRunId, businessDate),
                "dashboard",
                syncRunId
        );
        submit(
                statisticsExecutor,
                () -> statisticsSnapshotService.createInventorySnapshots(syncRunId, businessDate),
                "statistics",
                syncRunId
        );
    }

    private void submit(ExecutorService executor, Runnable task, String snapshotType, Long syncRunId) {
        try {
            executor.execute(() -> {
                try {
                    task.run();
                    log.info("inventory {} snapshot synchronized: syncRunId={}", snapshotType, syncRunId);
                } catch (Exception exception) {
                    // Snapshot work is intentionally best-effort and isolated from the canonical sync.
                    log.error("inventory {} snapshot synchronization failed: syncRunId={}", snapshotType, syncRunId,
                            exception);
                }
            });
        } catch (RejectedExecutionException exception) {
            // A full snapshot queue must not turn an already committed inventory run into a failure.
            log.error("inventory {} snapshot queue is full: syncRunId={}", snapshotType, syncRunId, exception);
        }
    }

    @PreDestroy
    void shutdown() {
        dashboardExecutor.shutdownNow();
        statisticsExecutor.shutdownNow();
    }

    private static ExecutorService newExecutor(String threadName) {
        return new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
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
