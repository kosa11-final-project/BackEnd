package com.stockit.backend.feature.strategy.approval;

import java.net.URI;
import java.net.http.HttpTimeoutException;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** 비밀 웹후크 URL을 통해 Reviewer 개인 Teams 채팅으로 카드를 전송한다. */
@Component
public class PowerAutomateTeamsApprovalSender implements TeamsApprovalSender {

    private final RestClient restClient;
    private final TeamsApprovalProperties properties;
    private final TeamsApprovalCardFactory cardFactory;

    public PowerAutomateTeamsApprovalSender(
            @Qualifier("teamsApprovalRestClient") RestClient restClient,
            TeamsApprovalProperties properties,
            TeamsApprovalCardFactory cardFactory
    ) {
        this.restClient = restClient;
        this.properties = properties;
        this.cardFactory = cardFactory;
    }

    @Override
    public void send(TeamsApprovalMessage message) {
        URI webhookUri = webhookUri();
        try {
            restClient.post()
                    .uri(webhookUri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(cardFactory.create(message))
                    .retrieve()
                    .toBodilessEntity();
        } catch (ResourceAccessException exception) {
            String code = containsCause(exception, HttpTimeoutException.class)
                    ? "TEAMS_WEBHOOK_TIMEOUT"
                    : "TEAMS_WEBHOOK_UNAVAILABLE";
            throw new TeamsApprovalDeliveryException(
                    code,
                    "Teams webhook connection failed",
                    exception
            );
        } catch (RestClientResponseException exception) {
            throw new TeamsApprovalDeliveryException(
                    "TEAMS_WEBHOOK_REJECTED",
                    "Teams webhook returned HTTP " + exception.getStatusCode().value(),
                    exception
            );
        } catch (RuntimeException exception) {
            throw new TeamsApprovalDeliveryException(
                    "TEAMS_WEBHOOK_REJECTED",
                    "Teams webhook rejected the request",
                    exception
            );
        }
    }

    private URI webhookUri() {
        if (!properties.isEnabled()
                || properties.getWebhookUrl() == null
                || properties.getWebhookUrl().isBlank()) {
            throw new TeamsApprovalDeliveryException(
                    "TEAMS_CONFIGURATION_INVALID",
                    "Teams webhook is not configured"
            );
        }
        try {
            URI uri = URI.create(properties.getWebhookUrl().trim());
            if (!uri.isAbsolute() || uri.getHost() == null
                    || !"https".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException("absolute HTTPS URL required");
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new TeamsApprovalDeliveryException(
                    "TEAMS_CONFIGURATION_INVALID",
                    "Teams webhook URL is invalid",
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
