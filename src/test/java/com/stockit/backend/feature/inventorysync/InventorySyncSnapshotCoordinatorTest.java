package com.stockit.backend.feature.inventorysync.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stockit.backend.feature.dashboard.service.DashboardSnapshotService;
import com.stockit.backend.feature.statistics.service.StatisticsSnapshotService;

@ExtendWith(MockitoExtension.class)
class InventorySyncSnapshotCoordinatorTest {
    private static final Long SYNC_RUN_ID = 101L;
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 8, 23);

    @Mock
    private DashboardSnapshotService dashboardSnapshotService;

    @Mock
    private StatisticsSnapshotService statisticsSnapshotService;

    private ExecutorService dashboardExecutor;
    private ExecutorService statisticsExecutor;
    private InventorySyncSnapshotCoordinator coordinator;

    @AfterEach
    void tearDown() {
        if (coordinator != null) coordinator.shutdown();
        if (dashboardExecutor != null) dashboardExecutor.shutdownNow();
        if (statisticsExecutor != null) statisticsExecutor.shutdownNow();
    }

    @Test
    void dashboardAndStatisticsRunIndependently() throws Exception {
        CountDownLatch dashboardStarted = new CountDownLatch(1);
        CountDownLatch releaseDashboard = new CountDownLatch(1);
        CountDownLatch statisticsFinished = new CountDownLatch(1);
        dashboardExecutor = Executors.newSingleThreadExecutor();
        statisticsExecutor = Executors.newSingleThreadExecutor();
        coordinator = new InventorySyncSnapshotCoordinator(
                dashboardSnapshotService,
                statisticsSnapshotService,
                dashboardExecutor,
                statisticsExecutor
        );

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

        verify(dashboardSnapshotService).createSnapshot(SYNC_RUN_ID, BUSINESS_DATE);
        verify(statisticsSnapshotService).createInventorySnapshots(SYNC_RUN_ID, BUSINESS_DATE);
    }

    @Test
    void dashboardFailureDoesNotPreventStatisticsSnapshot() throws Exception {
        CountDownLatch statisticsFinished = new CountDownLatch(1);
        dashboardExecutor = Executors.newSingleThreadExecutor();
        statisticsExecutor = Executors.newSingleThreadExecutor();
        coordinator = new InventorySyncSnapshotCoordinator(
                dashboardSnapshotService,
                statisticsSnapshotService,
                dashboardExecutor,
                statisticsExecutor
        );

        doThrow(new IllegalStateException("dashboard unavailable"))
                .when(dashboardSnapshotService).createSnapshot(eq(SYNC_RUN_ID), eq(BUSINESS_DATE));
        doAnswer(invocation -> {
            statisticsFinished.countDown();
            return java.util.List.of(2L);
        }).when(statisticsSnapshotService).createInventorySnapshots(SYNC_RUN_ID, BUSINESS_DATE);

        coordinator.scheduleAfterCommit(SYNC_RUN_ID, BUSINESS_DATE);

        assertThat(statisticsFinished.await(2, TimeUnit.SECONDS)).isTrue();
        verify(dashboardSnapshotService).createSnapshot(SYNC_RUN_ID, BUSINESS_DATE);
        verify(statisticsSnapshotService).createInventorySnapshots(SYNC_RUN_ID, BUSINESS_DATE);
    }
}
