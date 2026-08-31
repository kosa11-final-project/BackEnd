package com.stockit.backend.feature.strategy.alert;

import java.net.URI;
import java.net.http.HttpTimeoutException;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** IT-AI-전략 채널 Workflow로 최종 실패 Adaptive Card를 전송합니다. */
@Component
@ConditionalOnProperty(
        prefix = "app.ai-strategy.alert.teams",
        name = "enabled",
        havingValue = "true"
)
class AiStrategyTeamsAlertSender {
    private final RestClient restClient;
    private final AiStrategyTeamsAlertProperties properties;
    private final AiStrategyTeamsAlertCardFactory cardFactory;

    AiStrategyTeamsAlertSender(
            @Qualifier("aiStrategyTeamsAlertRestClient") RestClient restClient,
            AiStrategyTeamsAlertProperties properties,
            AiStrategyTeamsAlertCardFactory cardFactory
    ) {
        this.restClient = restClient;
        this.properties = properties;
        this.cardFactory = cardFactory;
    }

    void send(AiStrategyTeamsAlertMessage message) {
        URI webhookUri = webhookUri();
        AiStrategyTeamsAlertCardFactory.TeamsWebhookRequest payload =
                cardFactory.create(message);
        try {
            ResponseEntity<Void> response = restClient.post()
                    .uri(webhookUri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new AiStrategyTeamsAlertDeliveryException(
                        "TEAMS_ALERT_REJECTED",
                        "Teams alert webhook returned HTTP "
                                + response.getStatusCode().value()
                );
            }
        } catch (ResourceAccessException exception) {
            String code = containsCause(exception, HttpTimeoutException.class)
                    ? "TEAMS_ALERT_TIMEOUT"
                    : "TEAMS_ALERT_UNAVAILABLE";
            throw new AiStrategyTeamsAlertDeliveryException(
                    code,
                    "Teams alert webhook connection failed",
                    exception
            );
        } catch (RestClientResponseException exception) {
            throw new AiStrategyTeamsAlertDeliveryException(
                    "TEAMS_ALERT_REJECTED",
                    "Teams alert webhook returned HTTP "
                            + exception.getStatusCode().value(),
                    exception
            );
        } catch (RestClientException exception) {
            throw new AiStrategyTeamsAlertDeliveryException(
                    "TEAMS_ALERT_REJECTED",
                    "Teams alert webhook request failed",
                    exception
            );
        }
    }

    private URI webhookUri() {
        String value = properties.webhookUrl();
        if (!properties.enabled() || value == null || value.isBlank()) {
            throw new AiStrategyTeamsAlertDeliveryException(
                    "TEAMS_ALERT_CONFIGURATION_INVALID",
                    "Teams alert webhook is not configured"
            );
        }
        try {
            URI uri = URI.create(value.trim());
            if (!uri.isAbsolute() || uri.getHost() == null
                    || !"https".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException("absolute HTTPS URL required");
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new AiStrategyTeamsAlertDeliveryException(
                    "TEAMS_ALERT_CONFIGURATION_INVALID",
                    "Teams alert webhook URL is invalid",
                    exception
            );
        }
    }

    private static boolean containsCause(Throwable throwable, Class<?> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
