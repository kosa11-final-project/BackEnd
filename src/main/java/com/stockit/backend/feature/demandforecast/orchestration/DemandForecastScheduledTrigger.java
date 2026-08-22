package com.stockit.backend.feature.demandforecast.orchestration;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 매일 01:00 KST에 당일을 예측 시작일로 하는 수요예측 실행을 등록합니다. */
@Component
@ConditionalOnProperty(
        prefix = "app.demand-forecast.orchestration",
        name = "enabled",
        havingValue = "true"
)
public class DemandForecastScheduledTrigger {
    private static final Logger log = LoggerFactory.getLogger(DemandForecastScheduledTrigger.class);

    private final DemandForecastOrchestrationWorker worker;
    private final Clock clock;

    @Autowired
    public DemandForecastScheduledTrigger(
            DemandForecastOrchestrationWorker worker,
            @Value("${app.demand-forecast.orchestration.zone:Asia/Seoul}") String zone
    ) {
        this.worker = worker;
        this.clock = Clock.system(ZoneId.of(zone));
    }

    DemandForecastScheduledTrigger(DemandForecastOrchestrationWorker worker, Clock clock) {
        this.worker = worker;
        this.clock = clock;
    }

    @Scheduled(
            cron = "${app.demand-forecast.orchestration.cron:0 0 1 * * *}",
            zone = "${app.demand-forecast.orchestration.zone:Asia/Seoul}"
    )
    public void trigger() {
        LocalDate baseDate = LocalDate.now(clock);
        boolean launched = worker.launchScheduled(baseDate);
        log.info("Daily demand forecast schedule handled. baseDate={}, launched={}", baseDate, launched);
    }
}
