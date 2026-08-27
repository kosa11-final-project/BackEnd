package com.stockit.backend.feature.demandforecast.alert;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.stockit.backend.feature.demandforecast.domain.DemandForecastRunNotificationEvent;
import com.stockit.backend.feature.demandforecast.domain.DemandForecastSchedulerFailureEvent;

/** 파이프라인 커밋 이후 또는 스케줄 자체 실패를 Teams 운영 채널에 전달합니다. */
@Component
@ConditionalOnProperty(
        prefix = "app.demand-forecast.alert.teams",
        name = "enabled",
        havingValue = "true"
)
class DemandForecastTeamsAlertListener {
    private static final Logger log =
            LoggerFactory.getLogger(DemandForecastTeamsAlertListener.class);

    private final DemandForecastTeamsAlertSender sender;
    private final DemandForecastTeamsAlertProperties properties;
    private final Clock clock;
    private final Map<String, Instant> schedulerDeliveries = new HashMap<>();

    @Autowired
    DemandForecastTeamsAlertListener(
            DemandForecastTeamsAlertSender sender,
            DemandForecastTeamsAlertProperties properties
    ) {
        this(sender, properties, Clock.systemUTC());
    }

    DemandForecastTeamsAlertListener(
            DemandForecastTeamsAlertSender sender,
            DemandForecastTeamsAlertProperties properties,
            Clock clock
    ) {
        this.sender = sender;
        this.properties = properties;
        this.clock = clock;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRunNotification(DemandForecastRunNotificationEvent event) {
        if (!"DEMAND_FORECAST_FAILED".equals(event.notificationType())) {
            return;
        }
        deliver(new DemandForecastTeamsAlertMessage(
                event.title(),
                properties.resolvedEnvironment(),
                event.forecastRunId(),
                event.baseDate(),
                event.failedStage(),
                event.errorCode(),
                event.message(),
                event.azureJobId(),
                null,
                event.occurredAt(),
                event.deduplicationKey(),
                properties.dashboardUrl()
        ));
    }

    @EventListener
    public void onSchedulerFailure(DemandForecastSchedulerFailureEvent event) {
        if (!reserveSchedulerDelivery(event.deduplicationKey())) {
            log.warn("Duplicate demand forecast scheduler alert suppressed. key={}",
                    event.deduplicationKey());
            return;
        }
        try {
            sender.send(new DemandForecastTeamsAlertMessage(
                    "수요예측 스케줄러 실패",
                    properties.resolvedEnvironment(),
                    null,
                    event.baseDate(),
                    null,
                    event.errorCode(),
                    event.errorMessage(),
                    null,
                    event.schedulerName(),
                    event.occurredAt(),
                    event.deduplicationKey(),
                    properties.dashboardUrl()
            ));
        } catch (RuntimeException exception) {
            releaseSchedulerDelivery(event.deduplicationKey());
            log.error("Demand forecast scheduler Teams alert delivery failed. key={}",
                    event.deduplicationKey(), exception);
        }
    }

    private void deliver(DemandForecastTeamsAlertMessage message) {
        try {
            sender.send(message);
        } catch (RuntimeException exception) {
            log.error("Demand forecast Teams alert delivery failed. runId={}, key={}",
                    message.forecastRunId(), message.deduplicationKey(), exception);
        }
    }

    private synchronized boolean reserveSchedulerDelivery(String key) {
        Instant now = clock.instant();
        Instant lastDelivery = schedulerDeliveries.get(key);
        if (lastDelivery != null && now.isBefore(
                lastDelivery.plus(properties.resolvedSchedulerCooldown())
        )) {
            return false;
        }
        schedulerDeliveries.put(key, now);
        return true;
    }

    private synchronized void releaseSchedulerDelivery(String key) {
        schedulerDeliveries.remove(key);
    }
}
