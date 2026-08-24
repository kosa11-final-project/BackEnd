package com.stockit.backend.feature.inventorysync.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.stockit.backend.feature.inventorysync.vo.InventorySyncSnapshotTaskVO;

/** 저장된 snapshot/task 상태를 대조하고, due task만 제한적으로 다시 예약합니다. */
@Component
@ConditionalOnProperty(
        prefix = "app.inventory-sync.snapshot-recovery",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class InventorySyncSnapshotReconciler {
    private static final Logger log = LoggerFactory.getLogger(InventorySyncSnapshotReconciler.class);
    private static final int RECOVERY_BATCH_SIZE = 20;

    private final InventorySyncSnapshotTaskControlService taskControlService;
    private final InventorySyncSnapshotCoordinator coordinator;

    public InventorySyncSnapshotReconciler(InventorySyncSnapshotTaskControlService taskControlService,
                                           InventorySyncSnapshotCoordinator coordinator) {
        this.taskControlService = taskControlService;
        this.coordinator = coordinator;
    }

    @Scheduled(
            initialDelayString = "${app.inventory-sync.snapshot-recovery.initial-delay:30s}",
            fixedDelayString = "${app.inventory-sync.snapshot-recovery.fixed-delay:60s}"
    )
    public void reconcile() {
        taskControlService.reconcilePersistedAndExpiredTasks();
        List<InventorySyncSnapshotTaskVO> tasks = taskControlService.findRecoverableTasks(RECOVERY_BATCH_SIZE);
        tasks.forEach(coordinator::scheduleTask);
        if (!tasks.isEmpty()) {
            log.info("recoverable inventory snapshot tasks scheduled: count={}", tasks.size());
        }
    }
}
