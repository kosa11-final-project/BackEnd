package com.stockit.backend.feature.statistics.service;

import java.time.LocalDate;
import java.time.ZoneId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.strategy-statistics.schedule", name = "enabled", havingValue = "true")
public class StrategyStatisticsScheduledTrigger {
    private static final Logger log = LoggerFactory.getLogger(StrategyStatisticsScheduledTrigger.class);

    private final StrategyExecutionResultService resultService;
    private final ZoneId businessZone;

    public StrategyStatisticsScheduledTrigger(
            StrategyExecutionResultService resultService,
            @Value("${app.strategy-statistics.schedule.zone:Asia/Seoul}") String zone
    ) {
        this.resultService = resultService;
        this.businessZone = ZoneId.of(zone);
    }

    @Scheduled(
            cron = "${app.strategy-statistics.schedule.cron:0 0 4 * * *}",
            zone = "${app.strategy-statistics.schedule.zone:Asia/Seoul}"
    )
    public void trigger() {
        LocalDate businessDate = LocalDate.now(businessZone);
        resultService.process(businessDate);
        log.info("strategy execution results processed: businessDate={}", businessDate);
    }
}
