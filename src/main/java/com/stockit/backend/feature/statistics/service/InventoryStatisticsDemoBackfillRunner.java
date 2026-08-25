package com.stockit.backend.feature.statistics.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.stockit.backend.feature.statistics.dto.response.InventoryStatisticsDemoBackfillResponse;

@Component
@ConditionalOnProperty(
        prefix = "app.statistics.demo-backfill",
        name = "run-on-startup",
        havingValue = "true"
)
public class InventoryStatisticsDemoBackfillRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(InventoryStatisticsDemoBackfillRunner.class);

    private final InventoryStatisticsDemoBackfillService backfillService;

    public InventoryStatisticsDemoBackfillRunner(InventoryStatisticsDemoBackfillService backfillService) {
        this.backfillService = backfillService;
    }

    @Override
    public void run(ApplicationArguments args) {
        InventoryStatisticsDemoBackfillResponse result = backfillService.backfill(null, null);
        log.info(
                "재고 통계 데모 백필 완료: {}~{}, 요청 {}일, 생성 {}일/{}건, 건너뜀 {}일",
                result.fromDate(),
                result.toDate(),
                result.requestedDateCount(),
                result.createdDateCount(),
                result.createdSnapshotCount(),
                result.skippedDateCount()
        );
    }
}
