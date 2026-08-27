package com.stockit.backend.feature.demandforecast.alert;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class DemandForecastTeamsAlertSenderTest {
    private static final String WEBHOOK_URL = "https://example.test/teams-alert";

    private MockRestServiceServer server;
    private DemandForecastTeamsAlertSender sender;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        sender = new DemandForecastTeamsAlertSender(
                builder.build(),
                properties(WEBHOOK_URL),
                new DemandForecastTeamsAlertCardFactory()
        );
    }

    @Test
    void postsAdaptiveCardToConfiguredChannelWebhook() {
        server.expect(requestTo(WEBHOOK_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "type": "message",
                          "attachments": [
                            {
                              "contentType": "application/vnd.microsoft.card.adaptive",
                              "content": {
                                "type": "AdaptiveCard",
                                "version": "1.4"
                              }
                            }
                          ]
                        }
                        """, false))
                .andRespond(withSuccess());

        sender.send(message());

        server.verify();
    }

    @Test
    void classifiesServerErrorAsRejectedDelivery() {
        server.expect(requestTo(WEBHOOK_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> sender.send(message()))
                .isInstanceOf(DemandForecastTeamsAlertDeliveryException.class)
                .satisfies(exception -> org.assertj.core.api.Assertions.assertThat(
                        ((DemandForecastTeamsAlertDeliveryException) exception).code()
                ).isEqualTo("TEAMS_ALERT_REJECTED"));
    }

    @Test
    void classifiesRedirectAsRejectedDelivery() {
        server.expect(requestTo(WEBHOOK_URL)).andRespond(withStatus(HttpStatus.FOUND));

        assertThatThrownBy(() -> sender.send(message()))
                .isInstanceOf(DemandForecastTeamsAlertDeliveryException.class)
                .hasMessage("Teams alert webhook returned HTTP 302")
                .satisfies(exception -> org.assertj.core.api.Assertions.assertThat(
                        ((DemandForecastTeamsAlertDeliveryException) exception).code()
                ).isEqualTo("TEAMS_ALERT_REJECTED"));
    }

    @Test
    void rejectsNonHttpsWebhookUrl() {
        DemandForecastTeamsAlertSender invalidSender = new DemandForecastTeamsAlertSender(
                RestClient.create(),
                properties("http://example.test/teams-alert"),
                new DemandForecastTeamsAlertCardFactory()
        );

        assertThatThrownBy(() -> invalidSender.send(message()))
                .isInstanceOf(DemandForecastTeamsAlertDeliveryException.class)
                .satisfies(exception -> org.assertj.core.api.Assertions.assertThat(
                        ((DemandForecastTeamsAlertDeliveryException) exception).code()
                ).isEqualTo("TEAMS_ALERT_CONFIGURATION_INVALID"));
    }

    private static DemandForecastTeamsAlertProperties properties(String webhookUrl) {
        return new DemandForecastTeamsAlertProperties(
                true, webhookUrl, null, null, null, "test", null
        );
    }

    private static DemandForecastTeamsAlertMessage message() {
        return new DemandForecastTeamsAlertMessage(
                "일일 수요예측 실패",
                "test",
                15L,
                LocalDate.of(2026, 8, 26),
                "AZURE_POLLING",
                "AZURE_JOB_FAILED",
                "model failed",
                "azure-job-15",
                null,
                Instant.parse("2026-08-26T01:00:00Z"),
                "DEMAND_FORECAST:15:FAILED",
                null
        );
    }
}
