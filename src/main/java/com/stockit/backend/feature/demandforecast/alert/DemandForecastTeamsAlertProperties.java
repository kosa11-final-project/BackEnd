package com.stockit.backend.feature.demandforecast.alert;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.AssertTrue;

@Validated
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
    @AssertTrue(message = "webhookUrl must be a non-empty absolute HTTPS URL when enabled")
    public boolean isWebhookUrlValidWhenEnabled() {
        if (!enabled) {
            return true;
        }
        if (webhookUrl == null || webhookUrl.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(webhookUrl.trim());
            return uri.isAbsolute()
                    && uri.getHost() != null
                    && "https".equalsIgnoreCase(uri.getScheme());
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    @AssertTrue(message = "schedulerCooldown must be strictly positive when enabled")
    public boolean isSchedulerCooldownValidWhenEnabled() {
        return !enabled || schedulerCooldown != null
                && !schedulerCooldown.isZero()
                && !schedulerCooldown.isNegative();
    }

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
