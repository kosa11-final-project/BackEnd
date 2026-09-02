package com.stockit.backend.feature.strategy.alert;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.AssertTrue;

@Validated
@ConfigurationProperties(prefix = "app.ai-strategy.alert.teams")
public record AiStrategyTeamsAlertProperties(
        boolean enabled,
        String webhookUrl,
        Duration connectTimeout,
        Duration readTimeout,
        String environment,
        String caseUrlTemplate,
        String logUrlTemplate
) {
    @AssertTrue(message = "webhookUrl must be a non-empty absolute HTTPS URL when enabled")
    public boolean isWebhookUrlValidWhenEnabled() {
        return !enabled || isAbsoluteHttps(webhookUrl);
    }

    @AssertTrue(message = "caseUrlTemplate must resolve to an absolute HTTPS URL")
    public boolean isCaseUrlTemplateValid() {
        return isOptionalTemplateValid(caseUrlTemplate);
    }

    @AssertTrue(message = "logUrlTemplate must resolve to an absolute HTTPS URL")
    public boolean isLogUrlTemplateValid() {
        return isOptionalTemplateValid(logUrlTemplate);
    }

    public Duration resolvedConnectTimeout() {
        return positiveOrDefault(connectTimeout, Duration.ofSeconds(3), "connectTimeout");
    }

    public Duration resolvedReadTimeout() {
        return positiveOrDefault(readTimeout, Duration.ofSeconds(10), "readTimeout");
    }

    public String resolvedEnvironment() {
        return environment == null || environment.isBlank()
                ? "unknown"
                : environment.trim();
    }

    public String resolveCaseUrl(Long strategyCaseId) {
        return resolveTemplate(caseUrlTemplate, strategyCaseId);
    }

    public String resolveLogUrl(Long strategyCaseId) {
        return resolveTemplate(logUrlTemplate, strategyCaseId);
    }

    private static String resolveTemplate(String template, Long strategyCaseId) {
        if (template == null || template.isBlank() || strategyCaseId == null) {
            return null;
        }
        return template.trim().replace(
                "{strategyCaseId}", strategyCaseId.toString()
        );
    }

    private static boolean isOptionalTemplateValid(String template) {
        return template == null || template.isBlank()
                || isAbsoluteHttps(template.replace("{strategyCaseId}", "1"));
    }

    private static boolean isAbsoluteHttps(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(value.trim());
            return uri.isAbsolute() && uri.getHost() != null
                    && "https".equalsIgnoreCase(uri.getScheme());
        } catch (IllegalArgumentException exception) {
            return false;
        }
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
