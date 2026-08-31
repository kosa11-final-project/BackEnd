package com.stockit.backend.feature.strategy.alert;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class AiStrategyTeamsAlertPropertiesTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TeamsAlertPropertiesConfiguration.class);

    @Test
    void failsContextWhenEnabledWebhookUrlIsEmpty() {
        contextRunner
                .withPropertyValues(
                        "app.ai-strategy.alert.teams.enabled=true",
                        "app.ai-strategy.alert.teams.webhook-url="
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
    void resolvesCaseAndLogUrlTemplates() {
        contextRunner
                .withPropertyValues(
                        "app.ai-strategy.alert.teams.enabled=true",
                        "app.ai-strategy.alert.teams.webhook-url=https://example.test/hook",
                        "app.ai-strategy.alert.teams.case-url-template="
                                + "https://stockit.test/cases/{strategyCaseId}",
                        "app.ai-strategy.alert.teams.log-url-template="
                                + "https://logs.test/search/{strategyCaseId}"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    AiStrategyTeamsAlertProperties properties = context.getBean(
                            AiStrategyTeamsAlertProperties.class
                    );
                    assertThat(properties.resolveCaseUrl(123L))
                            .isEqualTo("https://stockit.test/cases/123");
                    assertThat(properties.resolveLogUrl(123L))
                            .isEqualTo("https://logs.test/search/123");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AiStrategyTeamsAlertProperties.class)
    static class TeamsAlertPropertiesConfiguration {
    }
}
