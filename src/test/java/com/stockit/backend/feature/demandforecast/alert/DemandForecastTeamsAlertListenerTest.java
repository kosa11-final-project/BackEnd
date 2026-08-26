package com.stockit.backend.feature.demandforecast.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stockit.backend.feature.demandforecast.domain.DemandForecastRunNotificationEvent;
import com.stockit.backend.feature.demandforecast.domain.DemandForecastSchedulerFailureEvent;

@ExtendWith(MockitoExtension.class)
class DemandForecastTeamsAlertListenerTest {

    @Mock
    private DemandForecastTeamsAlertSender sender;

    private DemandForecastTeamsAlertListener listener;

    @BeforeEach
    void setUp() {
        DemandForecastTeamsAlertProperties properties =
                new DemandForecastTeamsAlertProperties(
                        true, "https://example.test/webhook", null, null,
                        null, "production", "https://stockit.test/admin"
                );
        listener = new DemandForecastTeamsAlertListener(
                sender,
                properties,
                Clock.fixed(Instant.parse("2026-08-26T01:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void sendsFailedRunToTeamsChannel() {
        DemandForecastRunNotificationEvent event = new DemandForecastRunNotificationEvent(
                15L,
                "DEMAND_FORECAST_FAILED",
                "ERROR",
                "일일 수요예측 실패",
                "model failed",
                "DEMAND_FORECAST:15:FAILED",
                LocalDate.of(2026, 8, 26),
                "AZURE_POLLING",
                "AZURE_JOB_FAILED",
                "azure-job-15",
                Instant.parse("2026-08-26T01:00:00Z")
        );

        listener.onRunNotification(event);

        ArgumentCaptor<DemandForecastTeamsAlertMessage> captor =
                ArgumentCaptor.forClass(DemandForecastTeamsAlertMessage.class);
        verify(sender).send(captor.capture());
        assertThat(captor.getValue().environment()).isEqualTo("production");
        assertThat(captor.getValue().forecastRunId()).isEqualTo(15L);
        assertThat(captor.getValue().failedStage()).isEqualTo("AZURE_POLLING");
        assertThat(captor.getValue().azureJobId()).isEqualTo("azure-job-15");
    }

    @Test
    void ignoresCompletedRun() {
        DemandForecastRunNotificationEvent event = new DemandForecastRunNotificationEvent(
                15L,
                "DEMAND_FORECAST_COMPLETED",
                "INFO",
                "일일 수요예측 완료",
                "completed",
                "DEMAND_FORECAST:15:COMPLETED"
        );

        listener.onRunNotification(event);

        verify(sender, never()).send(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void suppressesRepeatedSchedulerFailureDuringCooldown() {
        DemandForecastSchedulerFailureEvent event = new DemandForecastSchedulerFailureEvent(
                "demand-forecast-azure-job-poller",
                null,
                "AZURE_POLLER_SCHEDULE_FAILED",
                "database unavailable",
                "DEMAND_FORECAST:SCHEDULER:AZURE_POLLER_FAILED",
                Instant.parse("2026-08-26T01:00:00Z")
        );

        listener.onSchedulerFailure(event);
        listener.onSchedulerFailure(event);

        verify(sender).send(org.mockito.ArgumentMatchers.any());
    }
}
