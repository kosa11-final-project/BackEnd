package com.stockit.backend.feature.strategy.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class StrategyNotificationRecoveryPropertiesTest {

    @Test
    void providesThreeDayLookbackAndHundredRowBatchByDefault() {
        StrategyNotificationRecoveryProperties properties =
                new StrategyNotificationRecoveryProperties();

        assertThat(properties.getLookback()).isEqualTo(Duration.ofDays(3));
        assertThat(properties.getBatchSize()).isEqualTo(100);
    }

    @Test
    void rejectsInvalidRecoveryBounds() {
        StrategyNotificationRecoveryProperties properties =
                new StrategyNotificationRecoveryProperties();

        assertThatThrownBy(() -> properties.setLookback(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.setBatchSize(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.setBatchSize(1001))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
