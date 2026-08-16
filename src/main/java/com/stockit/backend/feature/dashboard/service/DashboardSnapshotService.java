package com.stockit.backend.feature.dashboard.service;

import java.time.LocalDate;

public interface DashboardSnapshotService {

    /**
     * Creates one immutable dashboard snapshot after inventory synchronization,
     * demand forecasting, and risk assessment have completed successfully.
     * Repeated calls with the same sync job ID return the existing snapshot ID.
     */
    Long createSnapshot(Long syncJobId, LocalDate asOfDate);
}
