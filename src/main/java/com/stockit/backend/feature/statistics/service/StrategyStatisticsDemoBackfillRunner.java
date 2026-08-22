package com.stockit.backend.feature.statistics.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.stockit.backend.feature.statistics.dto.response.StrategyStatisticsDemoBackfillResponse;

@Component
@ConditionalOnProperty(
        prefix = "app.statistics.strategy-demo-backfill",
        name = "run-on-startup",
        havingValue = "true"
)
public class StrategyStatisticsDemoBackfillRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(StrategyStatisticsDemoBackfillRunner.class);

    private final StrategyStatisticsDemoBackfillService backfillService;

    public StrategyStatisticsDemoBackfillRunner(StrategyStatisticsDemoBackfillService backfillService) {
        this.backfillService = backfillService;
    }

    @Override
    public void run(ApplicationArguments args) {
        StrategyStatisticsDemoBackfillResponse result = backfillService.backfill(null, null);
        log.info(
                "AI 전략 통계 데모 백필 완료: {}~{}, 생성 {}/{}건, 액션 {}건, 건너뜀 {}건",
                result.fromDate(),
                result.toDate(),
                result.createdStrategyCount(),
                result.requestedStrategyCount(),
                result.createdActionCount(),
                result.skippedStrategyCount()
        );
    }
}
