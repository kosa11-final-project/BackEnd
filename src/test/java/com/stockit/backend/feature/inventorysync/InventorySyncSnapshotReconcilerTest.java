package com.stockit.backend.feature.inventorysync;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stockit.backend.feature.inventorysync.service.InventorySyncSnapshotCoordinator;
import com.stockit.backend.feature.inventorysync.service.InventorySyncSnapshotReconciler;
import com.stockit.backend.feature.inventorysync.service.InventorySyncSnapshotTaskControlService;
import com.stockit.backend.feature.inventorysync.vo.InventorySyncSnapshotTaskVO;

@ExtendWith(MockitoExtension.class)
class InventorySyncSnapshotReconcilerTest {
    @Mock InventorySyncSnapshotTaskControlService taskControlService;
    @Mock InventorySyncSnapshotCoordinator coordinator;

    @Test
    void repairsTerminalStateBeforeSchedulingOnlyMapperSelectedDueTasks() {
        InventorySyncSnapshotTaskVO dashboard = task(101L, "DASHBOARD");
        InventorySyncSnapshotTaskVO statistics = task(102L, "INVENTORY_STATISTICS");
        when(taskControlService.findRecoverableTasks(20)).thenReturn(List.of(dashboard, statistics));
        InventorySyncSnapshotReconciler reconciler =
                new InventorySyncSnapshotReconciler(taskControlService, coordinator);

        reconciler.reconcile();

        InOrder order = inOrder(taskControlService, coordinator);
        order.verify(taskControlService).reconcilePersistedAndExpiredTasks();
        order.verify(taskControlService).findRecoverableTasks(20);
        order.verify(coordinator).scheduleTask(dashboard);
        order.verify(coordinator).scheduleTask(statistics);
    }

    private static InventorySyncSnapshotTaskVO task(Long runId, String taskType) {
        InventorySyncSnapshotTaskVO task = new InventorySyncSnapshotTaskVO();
        task.setInventorySyncRunId(runId);
        task.setTaskType(taskType);
        task.setBusinessDate(LocalDate.of(2026, 8, 24));
        return task;
    }
}
