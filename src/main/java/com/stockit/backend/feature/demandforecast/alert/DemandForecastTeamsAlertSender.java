package com.stockit.backend.feature.demandforecast.alert;

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

/** 고정된 Teams IT 운영 채널 Workflow로 장애 카드를 전송합니다. */
@Component
@ConditionalOnProperty(
        prefix = "app.demand-forecast.alert.teams",
        name = "enabled",
        havingValue = "true"
)
class DemandForecastTeamsAlertSender {
    private final RestClient restClient;
    private final DemandForecastTeamsAlertProperties properties;
    private final DemandForecastTeamsAlertCardFactory cardFactory;

    DemandForecastTeamsAlertSender(
            @Qualifier("demandForecastTeamsAlertRestClient") RestClient restClient,
            DemandForecastTeamsAlertProperties properties,
            DemandForecastTeamsAlertCardFactory cardFactory
    ) {
        this.restClient = restClient;
        this.properties = properties;
        this.cardFactory = cardFactory;
    }

    void send(DemandForecastTeamsAlertMessage message) {
        URI webhookUri = webhookUri();
        DemandForecastTeamsAlertCardFactory.TeamsWebhookRequest payload =
                cardFactory.create(message);
        try {
            ResponseEntity<Void> response = restClient.post()
                    .uri(webhookUri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new DemandForecastTeamsAlertDeliveryException(
                        "TEAMS_ALERT_REJECTED",
                        "Teams alert webhook returned HTTP "
                                + response.getStatusCode().value()
                );
            }
        } catch (ResourceAccessException exception) {
            String code = containsCause(exception, HttpTimeoutException.class)
                    ? "TEAMS_ALERT_TIMEOUT"
                    : "TEAMS_ALERT_UNAVAILABLE";
            throw new DemandForecastTeamsAlertDeliveryException(
                    code,
                    "Teams alert webhook connection failed",
                    exception
            );
        } catch (RestClientResponseException exception) {
            throw new DemandForecastTeamsAlertDeliveryException(
                    "TEAMS_ALERT_REJECTED",
                    "Teams alert webhook returned HTTP " + exception.getStatusCode().value(),
                    exception
            );
        } catch (RestClientException exception) {
            throw new DemandForecastTeamsAlertDeliveryException(
                    "TEAMS_ALERT_REJECTED",
                    "Teams alert webhook request failed",
                    exception
            );
        }
    }

    private URI webhookUri() {
        String value = properties.webhookUrl();
        if (!properties.enabled() || value == null || value.isBlank()) {
            throw new DemandForecastTeamsAlertDeliveryException(
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
            throw new DemandForecastTeamsAlertDeliveryException(
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
