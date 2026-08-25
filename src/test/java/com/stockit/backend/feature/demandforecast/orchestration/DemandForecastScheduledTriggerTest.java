package com.stockit.backend.feature.demandforecast.orchestration;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DemandForecastScheduledTriggerTest {
    @Test
    void launchesCurrentForecastBaseDateAtScheduledTrigger() {
        DemandForecastOrchestrationWorker worker = Mockito.mock(DemandForecastOrchestrationWorker.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-22T01:00:00Z"),
                ZoneId.of("Asia/Seoul")
        );
        when(worker.launchScheduled(java.time.LocalDate.of(2026, 8, 22))).thenReturn(true);

        new DemandForecastScheduledTrigger(worker, clock).trigger();

        verify(worker).launchScheduled(java.time.LocalDate.of(2026, 8, 22));
    }
}
