package com.stockit.backend.feature.inventorysync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stockit.backend.feature.inventorysync.mapper.InventorySyncCanonicalMapper;
import com.stockit.backend.feature.inventorysync.mapper.InventorySyncRunMapper;
import com.stockit.backend.feature.inventorysync.mapper.InventorySyncRunSourceMapper;
import com.stockit.backend.feature.inventorysync.mapper.InventorySyncSourceWriteMapper;
import com.stockit.backend.feature.inventorysync.risk.InventorySyncRiskScopeSnapshotLoader;
import com.stockit.backend.feature.inventorysync.risk.InventorySyncRiskWriter;
import com.stockit.backend.feature.inventory.risk.RiskRuleEngine;
import com.stockit.backend.feature.inventorysync.service.InventorySyncCanonicalBatchWriter;
import com.stockit.backend.feature.inventorysync.service.InventorySyncSnapshotCoordinator;
import com.stockit.backend.feature.inventorysync.vo.InventorySyncRunVO;

@ExtendWith(MockitoExtension.class)
class InventorySyncCanonicalBatchWriterTest {

    @Test
    void finishRequiresActorBeforeRefreshingLotStatuses() {
        InventorySyncCanonicalMapper mapper = mock(InventorySyncCanonicalMapper.class);
        InventorySyncRunMapper runMapper = mock(InventorySyncRunMapper.class);
        InventorySyncSourceWriteMapper sourceWriteMapper = mock(InventorySyncSourceWriteMapper.class);
        InventorySyncRunSourceMapper runSourceMapper = mock(InventorySyncRunSourceMapper.class);
        InventorySyncRiskWriter riskWriter = mock(InventorySyncRiskWriter.class);
        InventorySyncRiskScopeSnapshotLoader riskSnapshotLoader = mock(InventorySyncRiskScopeSnapshotLoader.class);
        InventorySyncCanonicalBatchWriter writer = new InventorySyncCanonicalBatchWriter(
                mapper, runMapper, sourceWriteMapper, runSourceMapper, riskWriter, riskSnapshotLoader
        );

        assertThatThrownBy(() -> writer.finish("1", Map.of(), Set.of(), null, 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("actor");

        verifyNoInteractions(mapper, sourceWriteMapper, riskSnapshotLoader);
    }

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
        lenient().when(runMapper.selectByIdForUpdate(RUN_ID)).thenReturn(run);
        lenient().when(runMapper.updatePhase(RUN_ID, 1, 7L, "ASSESSING_RISK", 0L, 0L)).thenReturn(1);
        lenient().when(riskSnapshotLoader.findScopesRequiringDailyRefresh(any(LocalDate.class)))
                .thenReturn(Set.of());
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
    void noOpSourcePublishReassessesOutdatedRiskRulesAndReportsTheChanges() {
        Set<String> outdatedScopes = Set.of("1:10", "2:UNASSIGNED");
        when(riskSnapshotLoader.findScopesRequiringRuleVersion(RiskRuleEngine.RULE_VERSION))
                .thenReturn(outdatedScopes);

        int riskOnlyChanged = writer.finish("101", Map.of("OFFLINE", 1L), Set.of(), 7L, 0);

        assertThat(riskOnlyChanged).isEqualTo(2);
        verify(riskWriter).evaluateAndPersist(
                eq(RUN_ID), eq(7L), eq(outdatedScopes), any(LocalDate.class), eq(riskSnapshotLoader)
        );
        verify(snapshotCoordinator).scheduleAfterCommit(eq(RUN_ID), any(LocalDate.class));
    }

    @Test
    void changedPublishSchedulesDashboardAndStatisticsSnapshots() {
        writer.finish("101", Map.of("OFFLINE", 1L), Set.of(), 7L, 1);

        verify(snapshotCoordinator).scheduleAfterCommit(eq(RUN_ID), any(LocalDate.class));
    }

    @Test
    void refreshesCanonicalLotStatusesBeforeRiskAssessment() {
        writer.finish("101", Map.of("OFFLINE", 1L), Set.of("1:10"), 7L, 1);

        InOrder order = inOrder(canonicalMapper, riskWriter);
        order.verify(canonicalMapper).refreshLotStatuses(any(LocalDate.class), eq(7L));
        order.verify(riskWriter).evaluateAndPersist(
                eq(RUN_ID), eq(7L), eq(Set.of("1:10")), any(LocalDate.class), eq(riskSnapshotLoader)
        );
    }

    @Test
    void reassessesAndRefreshesSnapshotsForScopesWhoseDateBasedRiskChanged() {
        when(riskSnapshotLoader.findScopesRequiringDailyRefresh(any(LocalDate.class)))
                .thenReturn(Set.of("2:UNASSIGNED"));

        int changed = writer.finish("101", Map.of("OFFLINE", 1L), Set.of(), 7L, 0);

        assertThat(changed).isEqualTo(1);
        verify(riskWriter).evaluateAndPersist(
                eq(RUN_ID), eq(7L), eq(Set.of("2:UNASSIGNED")), any(LocalDate.class), eq(riskSnapshotLoader)
        );
        verify(snapshotCoordinator).scheduleAfterCommit(eq(RUN_ID), any(LocalDate.class));
    }

    @Test
    void pinsRiskDateAndForecastCutoffToRunStartAcrossMidnight() {
        Instant startedAt = Instant.parse("2026-08-27T14:30:00Z"); // 서울 2026-08-27 23:30
        InventorySyncRunVO run = new InventorySyncRunVO();
        run.setInventorySyncRunId(RUN_ID);
        run.setRunStatus("RUNNING");
        run.setMainAttemptNo(1);
        run.setFencingToken(7L);
        run.setStartedAt(startedAt);
        when(runMapper.selectByIdForUpdate(RUN_ID)).thenReturn(run);
        when(riskSnapshotLoader.findScopesRequiringDailyRefresh(LocalDate.of(2026, 8, 27)))
                .thenReturn(Set.of("2:UNASSIGNED"));

        writer.finish("101", Map.of("OFFLINE", 1L), Set.of(), 7L, 0);

        verify(canonicalMapper).refreshLotStatuses(LocalDate.of(2026, 8, 27), 7L);
        verify(riskWriter).evaluateAndPersist(
                eq(RUN_ID), eq(7L), eq(Set.of("2:UNASSIGNED")), eq(LocalDate.of(2026, 8, 27)),
                eq(startedAt), eq(riskSnapshotLoader)
        );
    }
}
