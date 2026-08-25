package com.stockit.backend.feature.inventorysync;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.stockit.backend.feature.inventorysync.adapter.EcommerceInventorySourceAdapter;
import com.stockit.backend.feature.inventorysync.adapter.GreetingInventorySourceAdapter;
import com.stockit.backend.feature.inventorysync.adapter.OfflineInventorySourceAdapter;
import com.stockit.backend.feature.inventorysync.adapter.WarehouseInventorySourceAdapter;
import com.stockit.backend.feature.inventorysync.mapper.InventorySyncRunMapper;
import com.stockit.backend.feature.inventorysync.mapper.InventorySyncRunSourceMapper;
import com.stockit.backend.feature.inventorysync.mapper.InventorySyncSourcePageMapper;
import com.stockit.backend.feature.inventorysync.service.InventorySyncPublisher;
import com.stockit.backend.feature.inventorysync.service.InventorySyncRunControlService;
import com.stockit.backend.feature.inventorysync.service.InventorySyncWorker;
import com.stockit.backend.feature.inventorysync.vo.InventorySyncRunVO;

class InventorySyncWorkerFailureTest {

    @Test
    void rethrowsAfterRecordingBusinessFailureSoBatchExecutionFails() {
        InventorySyncRunMapper runMapper = mock(InventorySyncRunMapper.class);
        InventorySyncSourcePageMapper sourceMapper = mock(InventorySyncSourcePageMapper.class);
        InventorySyncPublisher publisher = mock(InventorySyncPublisher.class);
        InventorySyncPublisher.CanonicalBatchWriter writer = mock(InventorySyncPublisher.CanonicalBatchWriter.class);
        InventorySyncRunSourceMapper runSourceMapper = mock(InventorySyncRunSourceMapper.class);
        InventorySyncRunControlService runControl = mock(InventorySyncRunControlService.class);

        InventorySyncRunVO run = new InventorySyncRunVO();
        run.setInventorySyncRunId(1L);
        run.setMainAttemptNo(0);
        run.setFencingToken(1L);
        run.setRequestedBy(99L);
        when(runMapper.selectById(1L)).thenReturn(run);
        when(runMapper.markRunning(eq(1L), eq(0), eq(1L), eq("local-worker"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(1);
        when(runMapper.updatePhase(anyLong(), anyInt(), anyLong(), anyString(), anyLong(), anyLong())).thenReturn(1);
        when(runMapper.selectSourceTypes()).thenThrow(new IllegalStateException("source state unavailable"));

        InventorySyncWorker worker = new InventorySyncWorker(
                runMapper, sourceMapper, publisher, writer, runSourceMapper, runControl,
                List.of(new OfflineInventorySourceAdapter(), new EcommerceInventorySourceAdapter(),
                        new GreetingInventorySourceAdapter(), new WarehouseInventorySourceAdapter())
        );

        assertThatThrownBy(() -> worker.execute(1L))
                .isInstanceOf(InventorySyncWorker.InventorySyncWorkerFailedException.class);
        verify(runControl).markWorkerFailed(1L, "OFFLINE", "MAIN", "SYNC_FAILED",
                "source state unavailable", 0, 1L, "FAILED");
    }
}
