package com.stockit.backend.feature.demandforecast.alert;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.demand-forecast.alert.teams")
public record DemandForecastTeamsAlertProperties(
        boolean enabled,
        String webhookUrl,
        Duration connectTimeout,
        Duration readTimeout,
        Duration schedulerCooldown,
        String environment,
        String dashboardUrl
) {
    public Duration resolvedConnectTimeout() {
        return positiveOrDefault(connectTimeout, Duration.ofSeconds(3), "connectTimeout");
    }

    public Duration resolvedReadTimeout() {
        return positiveOrDefault(readTimeout, Duration.ofSeconds(10), "readTimeout");
    }

    public Duration resolvedSchedulerCooldown() {
        return positiveOrDefault(schedulerCooldown, Duration.ofMinutes(10), "schedulerCooldown");
    }

    public String resolvedEnvironment() {
        return environment == null || environment.isBlank()
                ? "unknown"
                : environment.trim();
    }

    private static Duration positiveOrDefault(
            Duration value,
            Duration defaultValue,
            String name
    ) {
        if (value == null) {
            return defaultValue;
        }
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
