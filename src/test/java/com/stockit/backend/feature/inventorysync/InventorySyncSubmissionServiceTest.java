package com.stockit.backend.feature.inventorysync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentCaptor.forClass;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stockit.backend.feature.inventorysync.dto.InventorySyncStartRequest;
import com.stockit.backend.feature.inventorysync.mapper.InventorySyncRunMapper;
import com.stockit.backend.feature.inventorysync.service.InventorySyncBatchLauncher;
import com.stockit.backend.feature.inventorysync.service.InventorySyncSubmissionService;
import com.stockit.backend.feature.inventorysync.vo.InventorySyncRunVO;

@ExtendWith(MockitoExtension.class)
class InventorySyncSubmissionServiceTest {
    @Mock InventorySyncRunMapper runMapper;
    @Mock InventorySyncBatchLauncher launcher;
    private InventorySyncSubmissionService service;

    @BeforeEach
    void setUp() {
        when(runMapper.lockSubmissionGuard()).thenReturn(1);
        service = new InventorySyncSubmissionService(runMapper, launcher);
    }

    @Test
    void sameRequestHashReturnsExistingRunBeforeActiveLockCheck() {
        InventorySyncRunVO existing = run(11L, "client-1", "QUEUED");
        when(runMapper.selectByClientRequestId("client-1")).thenReturn(existing);

        var result = service.submit(new InventorySyncStartRequest("client-1"), 7L);

        assertEquals(200, result.httpStatus());
        assertEquals(11L, result.response().syncRunId());
        verify(runMapper, never()).selectActiveRun();
        verify(launcher, never()).launch(anyLong());
    }

    @Test
    void differentSessionIsRejectedWhenAnotherRunIsActive() {
        when(runMapper.selectByClientRequestId("client-2")).thenReturn(null);
        when(runMapper.countRecentRequests(anyLong(), any(Instant.class))).thenReturn(0);
        when(runMapper.selectActiveRun()).thenReturn(run(11L, "client-1", "RUNNING"));

        var result = service.submit(new InventorySyncStartRequest("client-2"), 7L);

        assertEquals(409, result.httpStatus());
        assertEquals(11L, result.response().syncRunId());
        verify(runMapper, never()).insertRun(any());
        verify(launcher, never()).launch(anyLong());
    }

    @Test
    void acceptedRunIsPersistedBeforeAsyncLauncher() {
        when(runMapper.selectByClientRequestId("client-3")).thenReturn(null);
        when(runMapper.countRecentRequests(anyLong(), any(Instant.class))).thenReturn(0);
        when(runMapper.selectActiveRun()).thenReturn(null);
        org.mockito.Mockito.doAnswer(invocation -> {
            InventorySyncRunVO run = invocation.getArgument(0);
            run.setInventorySyncRunId(12L);
            run.setRunStatus("QUEUED");
            run.setRequestedAt(Instant.now());
            return 1;
        }).when(runMapper).insertRun(any());

        var result = service.submit(new InventorySyncStartRequest("client-3"), 7L);

        assertEquals(202, result.httpStatus());
        assertEquals(12L, result.response().syncRunId());
        verify(launcher).launch(12L);
    }

    @Test
    void principalCooldownRejectsASecondAcceptedRequestBeforeInsert() {
        when(runMapper.selectByClientRequestId("client-4")).thenReturn(null);
        when(runMapper.selectLastRequestedAt(7L)).thenReturn(Instant.now().minusSeconds(5));

        var result = service.submit(new InventorySyncStartRequest("client-4"), 7L);

        assertEquals(429, result.httpStatus());
        org.junit.jupiter.api.Assertions.assertTrue(result.retryAfterSeconds() >= 1);
        verify(runMapper, never()).insertRun(any());
        verify(launcher, never()).launch(anyLong());
    }

    @Test
    void scheduledSubmissionUsesSystemTriggerAndDoesNotConsumeManualRateLimit() {
        when(runMapper.selectSystemUserId()).thenReturn(1L);
        when(runMapper.selectByClientRequestId("inventory-sync-scheduled-20260821")).thenReturn(null);
        when(runMapper.selectActiveRun()).thenReturn(null);
        org.mockito.Mockito.doAnswer(invocation -> {
            InventorySyncRunVO run = invocation.getArgument(0);
            run.setInventorySyncRunId(20L);
            run.setRunStatus("QUEUED");
            run.setRequestedAt(Instant.now());
            return 1;
        }).when(runMapper).insertRun(any());

        var result = service.submitScheduled(new InventorySyncStartRequest("inventory-sync-scheduled-20260821"));

        assertEquals(202, result.httpStatus());
        var captor = forClass(InventorySyncRunVO.class);
        verify(runMapper).insertRun(captor.capture());
        assertEquals("SCHEDULED", captor.getValue().getTriggerType());
        verify(runMapper, never()).selectLastRequestedAt(anyLong());
        verify(runMapper, never()).countRecentRequests(anyLong(), any(Instant.class));
        verify(launcher).launch(20L);
    }

    private static InventorySyncRunVO run(Long id, String requestId, String status) {
        InventorySyncRunVO run = new InventorySyncRunVO();
        run.setInventorySyncRunId(id);
        run.setClientRequestId(requestId);
        run.setRequestHash(InventorySyncSubmissionService.requestHash(new InventorySyncStartRequest(requestId)));
        run.setRunStatus(status);
        run.setRequestedAt(Instant.now());
        return run;
    }
}
