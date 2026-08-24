package com.stockit.backend.feature.inventorysync.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.TransientDataAccessResourceException;

import com.stockit.backend.feature.dashboard.service.DashboardSnapshotService;
import com.stockit.backend.feature.inventorysync.vo.InventorySyncSnapshotTaskVO;
import com.stockit.backend.feature.statistics.service.StatisticsSnapshotService;

@ExtendWith(MockitoExtension.class)
class InventorySyncSnapshotCoordinatorTest {
    private static final Long SYNC_RUN_ID = 101L;
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 8, 23);

    @Mock DashboardSnapshotService dashboardSnapshotService;
    @Mock StatisticsSnapshotService statisticsSnapshotService;
    @Mock InventorySyncSnapshotTaskControlService taskControlService;

    private ExecutorService dashboardExecutor;
    private ExecutorService statisticsExecutor;
    private InventorySyncSnapshotCoordinator coordinator;

    @BeforeEach
    void setUp() {
        dashboardExecutor = Executors.newSingleThreadExecutor();
        statisticsExecutor = Executors.newSingleThreadExecutor();
        coordinator = new InventorySyncSnapshotCoordinator(
                dashboardSnapshotService,
                statisticsSnapshotService,
                taskControlService,
                dashboardExecutor,
                statisticsExecutor,
                "test-worker"
        );
        when(taskControlService.claim(eq(SYNC_RUN_ID), anyString(), anyString(), any(Instant.class)))
                .thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        if (coordinator != null) coordinator.shutdown();
    }

    @Test
    void preparesDurableTasksBeforeSchedulingBothSnapshots() throws Exception {
        CountDownLatch dashboardFinished = new CountDownLatch(1);
        CountDownLatch statisticsFinished = new CountDownLatch(1);
        doAnswer(invocation -> {
            dashboardFinished.countDown();
            return 1L;
        }).when(dashboardSnapshotService).createSnapshot(SYNC_RUN_ID, BUSINESS_DATE);
        doAnswer(invocation -> {
            statisticsFinished.countDown();
            return java.util.List.of(2L);
        }).when(statisticsSnapshotService).createInventorySnapshots(SYNC_RUN_ID, BUSINESS_DATE);

        coordinator.scheduleAfterCommit(SYNC_RUN_ID, BUSINESS_DATE);

        assertThat(dashboardFinished.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(statisticsFinished.await(2, TimeUnit.SECONDS)).isTrue();
        verify(taskControlService).prepareTasks(SYNC_RUN_ID, BUSINESS_DATE);
    }

    @Test
    void dashboardAndStatisticsRunIndependently() throws Exception {
        CountDownLatch dashboardStarted = new CountDownLatch(1);
        CountDownLatch releaseDashboard = new CountDownLatch(1);
        CountDownLatch statisticsFinished = new CountDownLatch(1);
        doAnswer(invocation -> {
            dashboardStarted.countDown();
            releaseDashboard.await(2, TimeUnit.SECONDS);
            return 1L;
        }).when(dashboardSnapshotService).createSnapshot(SYNC_RUN_ID, BUSINESS_DATE);
        doAnswer(invocation -> {
            statisticsFinished.countDown();
            return java.util.List.of(2L);
        }).when(statisticsSnapshotService).createInventorySnapshots(SYNC_RUN_ID, BUSINESS_DATE);

        coordinator.scheduleAfterCommit(SYNC_RUN_ID, BUSINESS_DATE);

        assertThat(dashboardStarted.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(statisticsFinished.await(2, TimeUnit.SECONDS))
                .as("statistics must not wait for a slow dashboard snapshot")
                .isTrue();
        releaseDashboard.countDown();
    }

    @Test
    void dashboardFailureDoesNotPreventStatisticsSnapshot() throws Exception {
        CountDownLatch statisticsFinished = new CountDownLatch(1);
        doThrow(new IllegalStateException("dashboard unavailable"))
                .when(dashboardSnapshotService).createSnapshot(SYNC_RUN_ID, BUSINESS_DATE);
        doAnswer(invocation -> {
            statisticsFinished.countDown();
            return java.util.List.of(2L);
        }).when(statisticsSnapshotService).createInventorySnapshots(SYNC_RUN_ID, BUSINESS_DATE);

        coordinator.scheduleAfterCommit(SYNC_RUN_ID, BUSINESS_DATE);

        assertThat(statisticsFinished.await(2, TimeUnit.SECONDS)).isTrue();
        verify(statisticsSnapshotService).createInventorySnapshots(SYNC_RUN_ID, BUSINESS_DATE);
    }

    @Test
    void transientFailurePersistsRetryWaitWithoutBlockingWorkerThread() throws Exception {
        doThrow(new TransientDataAccessResourceException("temporary dashboard failure"))
                .when(dashboardSnapshotService).createSnapshot(SYNC_RUN_ID, BUSINESS_DATE);

        coordinator.scheduleAfterCommit(SYNC_RUN_ID, BUSINESS_DATE);
        dashboardExecutor.shutdown();
        assertThat(dashboardExecutor.awaitTermination(2, TimeUnit.SECONDS)).isTrue();

        verify(dashboardSnapshotService).createSnapshot(SYNC_RUN_ID, BUSINESS_DATE);
        verify(taskControlService).markRetryOrFailed(
                eq(SYNC_RUN_ID), eq(InventorySyncSnapshotCoordinator.DASHBOARD_TASK),
                anyString(), any(Instant.class), eq("SNAPSHOT_TRANSIENT_FAILURE"),
                eq("temporary dashboard failure")
        );
        verify(taskControlService, never()).markFailed(
                eq(SYNC_RUN_ID), eq(InventorySyncSnapshotCoordinator.DASHBOARD_TASK),
                anyString(), anyString(), anyString()
        );
    }

    @Test
    void deterministicFailureIsPersistedAsFailedWithoutRetry() throws Exception {
        doThrow(new IllegalStateException("invalid dashboard payload"))
                .when(dashboardSnapshotService).createSnapshot(SYNC_RUN_ID, BUSINESS_DATE);

        coordinator.scheduleAfterCommit(SYNC_RUN_ID, BUSINESS_DATE);
        dashboardExecutor.shutdown();
        assertThat(dashboardExecutor.awaitTermination(2, TimeUnit.SECONDS)).isTrue();

        verify(taskControlService).markFailed(
                eq(SYNC_RUN_ID), eq(InventorySyncSnapshotCoordinator.DASHBOARD_TASK),
                anyString(), eq("SNAPSHOT_DETERMINISTIC_FAILURE"), eq("invalid dashboard payload")
        );
        verify(taskControlService, never()).markRetryOrFailed(
                eq(SYNC_RUN_ID), eq(InventorySyncSnapshotCoordinator.DASHBOARD_TASK),
                anyString(), any(Instant.class), anyString(), anyString()
        );
    }

    @Test
    void duplicateRecoveryDoesNotQueueTheSameSnapshotTwice() throws Exception {
        CountDownLatch dashboardStarted = new CountDownLatch(1);
        CountDownLatch releaseDashboard = new CountDownLatch(1);
        doAnswer(invocation -> {
            dashboardStarted.countDown();
            releaseDashboard.await(2, TimeUnit.SECONDS);
            return 1L;
        }).when(dashboardSnapshotService).createSnapshot(SYNC_RUN_ID, BUSINESS_DATE);
        InventorySyncSnapshotTaskVO task = dashboardTask();

        coordinator.scheduleTask(task);
        assertThat(dashboardStarted.await(2, TimeUnit.SECONDS)).isTrue();
        coordinator.scheduleTask(task);
        releaseDashboard.countDown();
        dashboardExecutor.shutdown();
        assertThat(dashboardExecutor.awaitTermination(2, TimeUnit.SECONDS)).isTrue();

        verify(dashboardSnapshotService).createSnapshot(SYNC_RUN_ID, BUSINESS_DATE);
    }

    private static InventorySyncSnapshotTaskVO dashboardTask() {
        InventorySyncSnapshotTaskVO task = new InventorySyncSnapshotTaskVO();
        task.setInventorySyncRunId(SYNC_RUN_ID);
        task.setTaskType(InventorySyncSnapshotCoordinator.DASHBOARD_TASK);
        task.setBusinessDate(BUSINESS_DATE);
        return task;
    }
}
