package com.stockit.backend.feature.strategy.forecast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class StrategyForecastPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ForecastPropertiesConfiguration.class);

    @Test
    void acceptsDefaultTimeoutAndLockConfiguration() {
        StrategyForecastProperties properties = new StrategyForecastProperties();

        assertThatCode(properties::validateLockTtl).doesNotThrowAnyException();
        assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(properties.getReadTimeout()).isEqualTo(Duration.ofSeconds(60));
        assertThat(properties.getLockTtl()).isEqualTo(Duration.ofSeconds(180));
    }

    @Test
    void acceptsMinimumLockTtlIncludingSafetyMargin() {
        StrategyForecastProperties properties = new StrategyForecastProperties();
        properties.setConnectTimeout(Duration.ofSeconds(3));
        properties.setReadTimeout(Duration.ofSeconds(60));
        properties.setLockTtl(Duration.ofSeconds(93));

        assertThatCode(properties::validateLockTtl).doesNotThrowAnyException();
    }

    @Test
    void rejectsLockTtlBelowMinimumWithActualAndRequiredValues() {
        StrategyForecastProperties properties = new StrategyForecastProperties();
        properties.setConnectTimeout(Duration.ofSeconds(3));
        properties.setReadTimeout(Duration.ofSeconds(60));
        properties.setLockTtl(Duration.ofSeconds(92));

        assertThatThrownBy(properties::validateLockTtl)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "Forecast lockTtl is too short: required at least 93s, actual 92s"
                );
    }

    @Test
    void recalculatesMinimumWhenHttpTimeoutsChange() {
        StrategyForecastProperties properties = new StrategyForecastProperties();
        properties.setConnectTimeout(Duration.ofSeconds(10));
        properties.setReadTimeout(Duration.ofSeconds(120));
        properties.setLockTtl(Duration.ofSeconds(140));

        assertThatThrownBy(properties::validateLockTtl)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "Forecast lockTtl is too short: required at least 160s, actual 140s"
                );
    }

    @Test
    void keepsPositiveDurationValidation() {
        StrategyForecastProperties properties = new StrategyForecastProperties();

        assertThatThrownBy(() -> properties.setConnectTimeout(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("connectTimeout must be positive");
        assertThatThrownBy(() -> properties.setReadTimeout(Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("readTimeout must be positive");
        assertThatThrownBy(() -> properties.setResultTtl(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("resultTtl must be positive");
        assertThatThrownBy(() -> properties.setLockTtl(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("lockTtl must be positive");
    }

    @Test
    void failsApplicationContextWhenBoundLockTtlIsTooShort() {
        contextRunner
                .withPropertyValues(
                        "app.ai-strategy.forecast.connect-timeout=3s",
                        "app.ai-strategy.forecast.read-timeout=60s",
                        "app.ai-strategy.forecast.lock-ttl=92s"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining(
                                    "Forecast lockTtl is too short: required at least 93s, "
                                            + "actual 92s"
                            );
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(StrategyForecastProperties.class)
    static class ForecastPropertiesConfiguration {
    }
}
