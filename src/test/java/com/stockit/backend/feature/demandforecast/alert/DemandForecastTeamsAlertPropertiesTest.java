package com.stockit.backend.feature.demandforecast.alert;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class DemandForecastTeamsAlertPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TeamsAlertPropertiesConfiguration.class);

    @Test
    void failsApplicationContextWhenEnabledWebhookUrlIsEmpty() {
        contextRunner
                .withPropertyValues(
                        "app.demand-forecast.alert.teams.enabled=true",
                        "app.demand-forecast.alert.teams.webhook-url=",
                        "app.demand-forecast.alert.teams.scheduler-cooldown=10m"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining(
                                    "webhookUrl must be a non-empty absolute HTTPS URL when enabled"
                            );
                });
    }

    @Test
    void failsApplicationContextWhenEnabledSchedulerCooldownIsZero() {
        contextRunner
                .withPropertyValues(
                        "app.demand-forecast.alert.teams.enabled=true",
                        "app.demand-forecast.alert.teams.webhook-url=https://example.test/teams-alert",
                        "app.demand-forecast.alert.teams.scheduler-cooldown=0s"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining(
                                    "schedulerCooldown must be strictly positive when enabled"
                            );
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(DemandForecastTeamsAlertProperties.class)
    static class TeamsAlertPropertiesConfiguration {
    }
}
