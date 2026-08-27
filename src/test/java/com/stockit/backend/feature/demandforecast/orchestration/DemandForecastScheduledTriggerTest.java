package com.stockit.backend.feature.demandforecast.orchestration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

import com.stockit.backend.feature.demandforecast.domain.DemandForecastSchedulerFailureEvent;

class DemandForecastScheduledTriggerTest {
    @Test
    void launchesCurrentForecastBaseDateAtScheduledTrigger() {
        DemandForecastOrchestrationWorker worker = Mockito.mock(DemandForecastOrchestrationWorker.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-22T01:00:00Z"),
                ZoneId.of("Asia/Seoul")
        );
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        when(worker.launchScheduled(java.time.LocalDate.of(2026, 8, 22))).thenReturn(true);

        new DemandForecastScheduledTrigger(worker, clock, eventPublisher).trigger();

        verify(worker).launchScheduled(java.time.LocalDate.of(2026, 8, 22));
    }

    @Test
    void publishesSchedulerFailureAndRethrowsWhenRegistrationFails() {
        DemandForecastOrchestrationWorker worker = mock(DemandForecastOrchestrationWorker.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-22T01:00:00Z"),
                ZoneId.of("Asia/Seoul")
        );
        RuntimeException failure = new IllegalStateException("database unavailable");
        when(worker.launchScheduled(java.time.LocalDate.of(2026, 8, 22)))
                .thenThrow(failure);

        assertThatThrownBy(() -> new DemandForecastScheduledTrigger(
                worker, clock, eventPublisher
        ).trigger()).isSameAs(failure);

        verify(eventPublisher).publishEvent(any(DemandForecastSchedulerFailureEvent.class));
    }
}
