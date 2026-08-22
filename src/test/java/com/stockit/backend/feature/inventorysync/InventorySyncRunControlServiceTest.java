package com.stockit.backend.feature.inventorysync;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.stockit.backend.feature.inventorysync.mapper.InventorySyncRunMapper;
import com.stockit.backend.feature.inventorysync.mapper.InventorySyncRunSourceMapper;
import com.stockit.backend.feature.inventorysync.service.InventorySyncRunControlService;
import com.stockit.backend.feature.inventorysync.vo.InventorySyncRunVO;

class InventorySyncRunControlServiceTest {

    @Test
    void staleWorkerCannotMarkTheNewFencedAttemptFailed() {
        InventorySyncRunMapper runMapper = org.mockito.Mockito.mock(InventorySyncRunMapper.class);
        InventorySyncRunSourceMapper runSourceMapper = org.mockito.Mockito.mock(InventorySyncRunSourceMapper.class);
        InventorySyncRunControlService service = new InventorySyncRunControlService(runMapper, runSourceMapper);

        InventorySyncRunVO current = new InventorySyncRunVO();
        current.setInventorySyncRunId(10L);
        current.setRunStatus("RUNNING");
        current.setMainAttemptNo(2);
        current.setFencingToken(8L);
        when(runMapper.selectByIdForUpdate(10L)).thenReturn(current);

        service.markWorkerFailed(10L, "GREETING", "NORMALIZING", "SYNC_FAILED", "late worker", 1, 7L, "FAILED");

        verify(runSourceMapper, never()).markFailed(10L);
        verify(runMapper, never()).insertError(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        verify(runMapper, never()).complete(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void staleLauncherCannotMarkTheReplacementQueuedAttemptFailed() {
        InventorySyncRunMapper runMapper = org.mockito.Mockito.mock(InventorySyncRunMapper.class);
        InventorySyncRunSourceMapper runSourceMapper = org.mockito.Mockito.mock(InventorySyncRunSourceMapper.class);
        InventorySyncRunControlService service = new InventorySyncRunControlService(runMapper, runSourceMapper);

        InventorySyncRunVO current = new InventorySyncRunVO();
        current.setInventorySyncRunId(11L);
        current.setRunStatus("QUEUED");
        current.setMainAttemptNo(2);
        current.setFencingToken(8L);
        when(runMapper.selectByIdForUpdate(11L)).thenReturn(current);

        service.markLaunchFailed(11L, 1, 7L, "late launch callback");

        verify(runMapper, never()).markLaunchFailed(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString());
    }
}
