package com.stockit.backend.feature.strategy.notification;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 누락된 AI 전략 완료·실패 알림을 복구할 조회 범위와 처리량 */
@ConfigurationProperties(prefix = "app.ai-strategy.notification-recovery")
public class StrategyNotificationRecoveryProperties {

    private Duration lookback = Duration.ofDays(3);
    private int batchSize = 100;

    public Duration getLookback() {
        return lookback;
    }

    public void setLookback(Duration lookback) {
        if (lookback == null || lookback.isZero() || lookback.isNegative()) {
            throw new IllegalArgumentException("lookback must be positive");
        }
        this.lookback = lookback;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        if (batchSize < 1 || batchSize > 1000) {
            throw new IllegalArgumentException(
                    "batchSize must be between 1 and 1000"
            );
        }
        this.batchSize = batchSize;
    }
}
