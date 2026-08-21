package com.stockit.backend.feature.inventorysync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.stockit.backend.feature.inventorysync.dto.InventorySyncRunResponse;
import com.stockit.backend.feature.inventorysync.service.InventorySyncScheduledTrigger;
import com.stockit.backend.feature.inventorysync.service.InventorySyncSubmissionService;

class InventorySyncScheduledTriggerTest {

    @Test
    void usesBusinessDateAsAnIdempotentDailyClientRequestId() {
        assertThat(InventorySyncScheduledTrigger.scheduledClientRequestId(LocalDate.of(2026, 8, 21)))
                .isEqualTo("inventory-sync-scheduled-20260821");
    }

    @Test
    void registersTheScheduledRunThroughTheSameSubmissionService() {
        InventorySyncSubmissionService service = mock(InventorySyncSubmissionService.class);
        when(service.submitScheduled(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new InventorySyncSubmissionService.SubmissionResult(202,
                        InventorySyncRunResponse.from(run()), 0));

        new InventorySyncScheduledTrigger(service).trigger();

        verify(service).submitScheduled(org.mockito.ArgumentMatchers.argThat(request ->
                request.clientRequestId().startsWith("inventory-sync-scheduled-")));
    }

    private static com.stockit.backend.feature.inventorysync.vo.InventorySyncRunVO run() {
        var run = new com.stockit.backend.feature.inventorysync.vo.InventorySyncRunVO();
        run.setInventorySyncRunId(1L);
        run.setRunStatus("QUEUED");
        run.setTriggerType("SCHEDULED");
        return run;
    }
}
