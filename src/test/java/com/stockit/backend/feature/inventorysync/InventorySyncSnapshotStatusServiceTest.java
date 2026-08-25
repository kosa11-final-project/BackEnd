package com.stockit.backend.feature.inventorysync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stockit.backend.feature.inventorysync.mapper.InventorySyncRunMapper;
import com.stockit.backend.feature.inventorysync.service.InventorySyncSnapshotStatusService;
import com.stockit.backend.feature.inventorysync.vo.InventorySyncRunVO;
import com.stockit.backend.feature.inventorysync.vo.InventorySyncSnapshotStateVO;

@ExtendWith(MockitoExtension.class)
class InventorySyncSnapshotStatusServiceTest {

    @Mock InventorySyncRunMapper runMapper;

    @Test
    void reportsEachPersistedSnapshotIndependentlyAfterAChangedRun() {
        InventorySyncSnapshotStatusService service = new InventorySyncSnapshotStatusService(runMapper);
        InventorySyncRunVO run = succeededRun(101L, 12L);
        InventorySyncSnapshotStateVO state = new InventorySyncSnapshotStateVO();
        state.setDashboardReady(1);
        state.setInventoryStatisticsReady(0);
        state.setDashboardStatus("SUCCEEDED");
        state.setInventoryStatisticsStatus("RETRY_WAIT");
        when(runMapper.selectSnapshotState(101L)).thenReturn(state);

        var status = service.resolve(run);

        assertThat(status.required()).isTrue();
        assertThat(status.dashboardReady()).isTrue();
        assertThat(status.inventoryStatisticsReady()).isFalse();
        assertThat(status.dashboardStatus()).isEqualTo("SUCCEEDED");
        assertThat(status.inventoryStatisticsStatus()).isEqualTo("RETRY_WAIT");
    }

    @Test
    void noOpRunDoesNotProbeSnapshotTables() {
        InventorySyncSnapshotStatusService service = new InventorySyncSnapshotStatusService(runMapper);

        var status = service.resolve(succeededRun(102L, 0L));

        assertThat(status.required()).isFalse();
        assertThat(status.dashboardReady()).isNull();
        assertThat(status.inventoryStatisticsReady()).isNull();
        assertThat(status.dashboardStatus()).isNull();
        assertThat(status.inventoryStatisticsStatus()).isNull();
        verify(runMapper, never()).selectSnapshotState(102L);
    }

    private static InventorySyncRunVO succeededRun(Long runId, long changedCount) {
        InventorySyncRunVO run = new InventorySyncRunVO();
        run.setInventorySyncRunId(runId);
        run.setRunStatus("SUCCEEDED");
        run.setChangedCount(changedCount);
        return run;
    }
}
