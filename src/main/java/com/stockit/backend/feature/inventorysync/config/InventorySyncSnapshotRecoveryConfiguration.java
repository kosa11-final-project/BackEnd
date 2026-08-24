package com.stockit.backend.feature.inventorysync.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 수동 동기화만 쓰는 환경에서도 미완료 스냅샷 작업은 복구할 수 있어야 합니다. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
        prefix = "app.inventory-sync.snapshot-recovery",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class InventorySyncSnapshotRecoveryConfiguration {
}
