package com.stockit.backend.feature.inventorysync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stockit.backend.feature.inventorysync.mapper.InventorySyncSnapshotTaskMapper;
import com.stockit.backend.feature.inventorysync.service.InventorySyncSnapshotTaskControlService;
import com.stockit.backend.feature.inventorysync.vo.InventorySyncSnapshotTaskVO;

@ExtendWith(MockitoExtension.class)
class InventorySyncSnapshotTaskControlServiceTest {
    @Mock InventorySyncSnapshotTaskMapper mapper;

    @Test
    void preparesBothTasksInTheCallingTransaction() {
        InventorySyncSnapshotTaskControlService service = new InventorySyncSnapshotTaskControlService(mapper);
        LocalDate businessDate = LocalDate.of(2026, 8, 24);

        service.prepareTasks(101L, businessDate);

        verify(mapper).insertPendingTasks(101L, businessDate);
    }

    @Test
    void returnsWhetherTheLeaseOwnerActuallyUpdatedTheTask() {
        InventorySyncSnapshotTaskControlService service = new InventorySyncSnapshotTaskControlService(mapper);
        when(mapper.claimTask(eq(101L), eq("DASHBOARD"), eq("worker"), any(Instant.class)))
                .thenReturn(1);
        when(mapper.markSucceeded(101L, "DASHBOARD", "worker")).thenReturn(0);

        assertThat(service.claim(101L, "DASHBOARD", "worker", Instant.now())).isTrue();
        assertThat(service.markSucceeded(101L, "DASHBOARD", "worker")).isFalse();
    }

    @Test
    void persistsOracleByteSafeErrorText() {
        InventorySyncSnapshotTaskControlService service = new InventorySyncSnapshotTaskControlService(mapper);
        String longMessage = "가".repeat(900);

        service.markFailed(101L, "DASHBOARD", "worker", "X".repeat(150), longMessage);

        verify(mapper).markFailed(
                eq(101L), eq("DASHBOARD"), eq("worker"),
                eq("X".repeat(100)), eq("가".repeat(500))
        );
    }

    @Test
    void reconcilesPersistedRowsBeforeFailingAnExhaustedLease() {
        InventorySyncSnapshotTaskControlService service = new InventorySyncSnapshotTaskControlService(mapper);

        service.reconcilePersistedAndExpiredTasks();

        InOrder order = inOrder(mapper);
        order.verify(mapper).markPersistedSnapshotsSucceeded();
        order.verify(mapper).markExpiredExhaustedTasksFailed();
    }

    @Test
    void limitsRecoveryQueriesToPositiveBatches() {
        InventorySyncSnapshotTaskControlService service = new InventorySyncSnapshotTaskControlService(mapper);
        InventorySyncSnapshotTaskVO task = new InventorySyncSnapshotTaskVO();
        when(mapper.selectRecoverableTasks(20)).thenReturn(List.of(task));

        assertThat(service.findRecoverableTasks(20)).containsExactly(task);
        assertThatThrownBy(() -> service.findRecoverableTasks(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
