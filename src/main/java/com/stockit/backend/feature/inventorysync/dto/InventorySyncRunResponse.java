package com.stockit.backend.feature.inventorysync.dto;

import java.time.Instant;
import java.util.List;

import com.stockit.backend.feature.inventorysync.vo.InventorySyncRunVO;
import com.stockit.backend.feature.inventorysync.vo.InventorySyncRunSourceVO;
import com.stockit.backend.feature.inventorysync.vo.InventorySyncSourceStateVO;

public record InventorySyncRunResponse(
        Long syncRunId,
        String clientRequestId,
        String triggerType,
        String status,
        String phase,
        int mainAttemptNo,
        long readCount,
        long mappedCount,
        long changedCount,
        long errorCount,
        Instant requestedAt,
        Instant startedAt,
        Instant completedAt,
        String errorCode,
        String errorMessage,
        List<SourceState> sourceStates,
        List<RunSource> sourceRuns,
        SnapshotRefresh snapshotRefresh
) {
    public static InventorySyncRunResponse from(InventorySyncRunVO run) {
        return new InventorySyncRunResponse(
                run.getInventorySyncRunId(), run.getClientRequestId(), run.getTriggerType(), run.getRunStatus(),
                run.getCurrentPhase(), run.getMainAttemptNo(), run.getReadCount(), run.getMappedCount(), run.getChangedCount(),
                run.getErrorCount(), run.getRequestedAt(), run.getStartedAt(), run.getCompletedAt(),
                run.getErrorCode(), run.getErrorMessage(), List.of(), List.of(), SnapshotRefresh.notRequired()
        );
    }

    public static InventorySyncRunResponse from(InventorySyncRunVO run, List<InventorySyncSourceStateVO> states,
                                                List<InventorySyncRunSourceVO> sources,
                                                SnapshotRefresh snapshotRefresh) {
        InventorySyncRunResponse base = from(run);
        return new InventorySyncRunResponse(base.syncRunId(), base.clientRequestId(), base.triggerType(), base.status(), base.phase(), base.mainAttemptNo(),
                base.readCount(), base.mappedCount(), base.changedCount(), base.errorCount(), base.requestedAt(),
                base.startedAt(), base.completedAt(), base.errorCode(), base.errorMessage(),
                states == null ? List.of() : states.stream().map(SourceState::from).toList(),
                sources == null ? List.of() : sources.stream().map(RunSource::from).toList(),
                snapshotRefresh == null ? SnapshotRefresh.notRequired() : snapshotRefresh);
    }

    /** required=false일 때 ready/status 값은 적용 대상이 아니므로 null입니다. */
    public record SnapshotRefresh(
            boolean required,
            Boolean dashboardReady,
            Boolean inventoryStatisticsReady,
            String dashboardStatus,
            String inventoryStatisticsStatus
    ) {
        public static SnapshotRefresh notRequired() {
            return new SnapshotRefresh(false, null, null, null, null);
        }

        public static SnapshotRefresh required(boolean dashboardReady, boolean inventoryStatisticsReady,
                                               String dashboardStatus, String inventoryStatisticsStatus) {
            return new SnapshotRefresh(true, dashboardReady, inventoryStatisticsReady,
                    dashboardStatus, inventoryStatisticsStatus);
        }
    }

    public record SourceState(String sourceType, long currentVersion, long currentRecordCount, long pendingRecordCount,
                              Instant lastChangedAt, Instant lastSuccessSyncedAt) {
        static SourceState from(InventorySyncSourceStateVO value) {
            return new SourceState(value.getSourceType(), value.getCurrentVersion(), value.getCurrentRecordCount(),
                    value.getPendingRecordCount(), value.getLastChangedAt(), value.getLastSuccessSyncedAt());
        }
    }

    public record RunSource(String sourceType, String status, long readCount, long mappedCount,
                            long changedCount, long errorCount) {
        static RunSource from(InventorySyncRunSourceVO value) {
            return new RunSource(value.getSourceType(), value.getSourceStatus(), value.getReadCount(),
                    value.getMappedCount(), value.getChangedCount(), value.getErrorCount());
        }
    }
}
