package com.stockit.backend.feature.demandforecast.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.stockit.backend.feature.demandforecast.domain.DemandForecastRunNotificationEvent;
import com.stockit.backend.feature.demandforecast.mapper.DemandForecastOrchestrationMapper;
import com.stockit.backend.feature.demandforecast.vo.DemandForecastRunVO;

@ExtendWith(MockitoExtension.class)
class DemandForecastRunControlServiceTest {

    @Mock
    private DemandForecastOrchestrationMapper mapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private DemandForecastRunControlService service;

    @Test
    void publishesStructuredFailureEventAfterRunIsMarkedFailed() {
        DemandForecastRunVO run = new DemandForecastRunVO();
        run.setForecastRunId(15L);
        run.setBaseDate(LocalDate.of(2026, 8, 26));
        run.setCurrentStage("AZURE_POLLING");
        run.setAzureJobId("azure-job-15");
        when(mapper.selectSystemUserId()).thenReturn(99L);
        when(mapper.markFailed(15L, "AZURE_JOB_FAILED", "model failed", 99L))
                .thenReturn(1);

        service.fail(run, "AZURE_JOB_FAILED", "model failed");

        ArgumentCaptor<DemandForecastRunNotificationEvent> captor =
                ArgumentCaptor.forClass(DemandForecastRunNotificationEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        DemandForecastRunNotificationEvent event = captor.getValue();
        assertThat(event.baseDate()).isEqualTo(LocalDate.of(2026, 8, 26));
        assertThat(event.failedStage()).isEqualTo("AZURE_POLLING");
        assertThat(event.errorCode()).isEqualTo("AZURE_JOB_FAILED");
        assertThat(event.azureJobId()).isEqualTo("azure-job-15");
    }
}
