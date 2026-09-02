package com.stockit.backend.feature.demandforecast.orchestration;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;

import com.stockit.backend.feature.demandforecast.domain.DemandForecastSchedulerFailureEvent;
import com.stockit.backend.feature.demandforecast.mapper.DemandForecastOrchestrationMapper;
import com.stockit.backend.feature.demandforecast.vo.DemandForecastRunVO;

class DemandForecastAzureJobPollerTest {

    private static final String TIMEOUT_MESSAGE =
            "수요예측 파이프라인이 제한 시간 안에 완료되지 않았습니다.";

    private final DemandForecastOrchestrationMapper mapper =
            mock(DemandForecastOrchestrationMapper.class);
    private final DemandForecastRunControlService runControl =
            mock(DemandForecastRunControlService.class);
    private final DemandForecastFastApiClient fastApiClient =
            mock(DemandForecastFastApiClient.class);
    private final ApplicationEventPublisher eventPublisher =
            mock(ApplicationEventPublisher.class);
    private final DemandForecastAzureJobPoller poller = new DemandForecastAzureJobPoller(
            mapper,
            runControl,
            fastApiClient,
            new DemandForecastOrchestrationProperties(
                    true, null, null, null, null, null, null, null, null,
                    Duration.ofHours(2)
            ),
            eventPublisher
    );

    @Test
    void continuesTimeoutHandlingAndAzurePollingAfterOneTimeoutFailure() {
        DemandForecastRunVO failedTimeoutRun = run(1L, "timeout-job-1");
        DemandForecastRunVO nextTimeoutRun = run(2L, "timeout-job-2");
        DemandForecastRunVO azurePollingRun = run(3L, "azure-job-3");
        when(mapper.selectTimedOutRuns(7200L))
                .thenReturn(List.of(failedTimeoutRun, nextTimeoutRun));
        when(mapper.selectAzurePollingRuns()).thenReturn(List.of(azurePollingRun));
        doThrow(new IllegalStateException("timeout failure"))
                .when(runControl).fail(failedTimeoutRun, "PIPELINE_TIMED_OUT", TIMEOUT_MESSAGE);
        when(fastApiClient.status("azure-job-3"))
                .thenReturn(new DemandForecastFastApiClient.StatusResponse("RUNNING", null));
        when(runControl.requiredSystemUserId()).thenReturn(99L);

        poller.poll();

        verify(runControl).fail(nextTimeoutRun, "PIPELINE_TIMED_OUT", TIMEOUT_MESSAGE);
        verify(fastApiClient).status("azure-job-3");
        verify(mapper).touchAzurePolling(3L, 99L);
    }

    @Test
    void continuesAzurePollingAfterOneRunFails() {
        DemandForecastRunVO failedRun = run(1L, "azure-job-1");
        DemandForecastRunVO nextRun = run(2L, "azure-job-2");
        when(mapper.selectTimedOutRuns(7200L)).thenReturn(List.of());
        when(mapper.selectAzurePollingRuns()).thenReturn(List.of(failedRun, nextRun));
        when(fastApiClient.status("azure-job-1"))
                .thenThrow(new IllegalStateException("status failure"));
        when(fastApiClient.status("azure-job-2"))
                .thenReturn(new DemandForecastFastApiClient.StatusResponse("RUNNING", null));
        when(runControl.requiredSystemUserId()).thenReturn(99L);

        poller.poll();

        verify(fastApiClient).status("azure-job-2");
        verify(mapper).touchAzurePolling(2L, 99L);
    }

    @Test
    void publishesSchedulerFailureWhenPollingQueryFails() {
        when(mapper.selectTimedOutRuns(7200L))
                .thenThrow(new IllegalStateException("database unavailable"));

        poller.poll();

        verify(eventPublisher).publishEvent(any(DemandForecastSchedulerFailureEvent.class));
    }

    @Test
    void requestsCosmosDailyImportBeforeBackendImportWhenJobCompletes() {
        DemandForecastRunVO completedRun = run(1L, "azure-job-1");
        when(mapper.selectTimedOutRuns(7200L)).thenReturn(List.of());
        when(mapper.selectAzurePollingRuns()).thenReturn(List.of(completedRun));
        when(fastApiClient.status("azure-job-1"))
                .thenReturn(new DemandForecastFastApiClient.StatusResponse("COMPLETED", null));
        when(runControl.requiredSystemUserId()).thenReturn(99L);
        when(mapper.markImportRequested(1L, 99L)).thenReturn(1);

        poller.poll();

        InOrder importOrder = inOrder(fastApiClient);
        importOrder.verify(fastApiClient).requestDailyImport("azure-job-1");
        importOrder.verify(fastApiClient).requestImport("azure-job-1");
        verify(mapper).markImportRequested(1L, 99L);
    }

    private static DemandForecastRunVO run(Long runId, String azureJobId) {
        DemandForecastRunVO run = new DemandForecastRunVO();
        run.setForecastRunId(runId);
        run.setAzureJobId(azureJobId);
        return run;
    }
}
