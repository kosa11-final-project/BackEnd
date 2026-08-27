package com.stockit.backend.feature.strategy.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AiStrategySsePropertiesTest {

    @Test
    void limitsManagedConnectionsToFiveByDefault() {
        assertThat(new AiStrategySseProperties()
                .getMaxConnectionsPerSession()).isEqualTo(5);
    }

    @Test
    void rejectsUnsafeConnectionLimits() {
        AiStrategySseProperties properties = new AiStrategySseProperties();

        assertThatThrownBy(() -> properties.setMaxConnectionsPerSession(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.setMaxConnectionsPerSession(21))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
