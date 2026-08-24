package com.stockit.backend.feature.inventorysync;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stockit.backend.feature.inventorysync.mapper.InventorySyncCanonicalMapper;
import com.stockit.backend.feature.inventorysync.mapper.InventorySyncRunMapper;
import com.stockit.backend.feature.inventorysync.mapper.InventorySyncRunSourceMapper;
import com.stockit.backend.feature.inventorysync.mapper.InventorySyncSourceWriteMapper;
import com.stockit.backend.feature.inventorysync.risk.InventorySyncRiskScopeSnapshotLoader;
import com.stockit.backend.feature.inventorysync.risk.InventorySyncRiskWriter;
import com.stockit.backend.feature.inventorysync.service.InventorySyncCanonicalBatchWriter;
import com.stockit.backend.feature.inventorysync.service.InventorySyncSnapshotCoordinator;
import com.stockit.backend.feature.inventorysync.vo.InventorySyncRunVO;

@ExtendWith(MockitoExtension.class)
class InventorySyncCanonicalBatchWriterTest {
    private static final Long RUN_ID = 101L;

    @Mock InventorySyncCanonicalMapper canonicalMapper;
    @Mock InventorySyncRunMapper runMapper;
    @Mock InventorySyncSourceWriteMapper sourceWriteMapper;
    @Mock InventorySyncRunSourceMapper runSourceMapper;
    @Mock InventorySyncRiskWriter riskWriter;
    @Mock InventorySyncRiskScopeSnapshotLoader riskSnapshotLoader;
    @Mock InventorySyncSnapshotCoordinator snapshotCoordinator;

    private InventorySyncCanonicalBatchWriter writer;

    @BeforeEach
    void setUp() {
        InventorySyncRunVO run = new InventorySyncRunVO();
        run.setInventorySyncRunId(RUN_ID);
        run.setRunStatus("RUNNING");
        run.setMainAttemptNo(1);
        run.setFencingToken(7L);
        when(runMapper.selectByIdForUpdate(RUN_ID)).thenReturn(run);
        when(runMapper.updatePhase(RUN_ID, 1, 7L, "ASSESSING_RISK", 0L, 0L)).thenReturn(1);
        writer = new InventorySyncCanonicalBatchWriter(
                canonicalMapper,
                runMapper,
                sourceWriteMapper,
                runSourceMapper,
                riskWriter,
                riskSnapshotLoader,
                snapshotCoordinator
        );
    }

    @Test
    void noOpPublishDoesNotCreateDashboardOrStatisticsSnapshots() {
        writer.finish("101", Map.of("OFFLINE", 1L), Set.of(), 7L, 0);

        verify(snapshotCoordinator, never()).scheduleAfterCommit(eq(RUN_ID), any(LocalDate.class));
    }

    @Test
    void changedPublishSchedulesDashboardAndStatisticsSnapshots() {
        writer.finish("101", Map.of("OFFLINE", 1L), Set.of(), 7L, 1);

        verify(snapshotCoordinator).scheduleAfterCommit(eq(RUN_ID), any(LocalDate.class));
    }
}
