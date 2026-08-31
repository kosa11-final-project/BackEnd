package com.stockit.backend.feature.strategy.alert;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AiStrategyTeamsAlertSenderTest {
    private static final String WEBHOOK_URL = "https://example.test/teams-alert";

    private MockRestServiceServer server;
    private AiStrategyTeamsAlertSender sender;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        sender = new AiStrategyTeamsAlertSender(
                builder.build(),
                properties(WEBHOOK_URL),
                new AiStrategyTeamsAlertCardFactory()
        );
    }

    @Test
    void postsFinalFailureAdaptiveCardToConfiguredWebhook() {
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
                .isInstanceOf(AiStrategyTeamsAlertDeliveryException.class)
                .satisfies(exception -> org.assertj.core.api.Assertions.assertThat(
                        ((AiStrategyTeamsAlertDeliveryException) exception).code()
                ).isEqualTo("TEAMS_ALERT_REJECTED"));
    }

    @Test
    void rejectsNonHttpsWebhookUrl() {
        AiStrategyTeamsAlertSender invalidSender = new AiStrategyTeamsAlertSender(
                RestClient.create(),
                properties("http://example.test/teams-alert"),
                new AiStrategyTeamsAlertCardFactory()
        );

        assertThatThrownBy(() -> invalidSender.send(message()))
                .isInstanceOf(AiStrategyTeamsAlertDeliveryException.class)
                .satisfies(exception -> org.assertj.core.api.Assertions.assertThat(
                        ((AiStrategyTeamsAlertDeliveryException) exception).code()
                ).isEqualTo("TEAMS_ALERT_CONFIGURATION_INVALID"));
    }

    private static AiStrategyTeamsAlertProperties properties(String webhookUrl) {
        return new AiStrategyTeamsAlertProperties(
                true, webhookUrl, null, null, "test", null, null
        );
    }

    private static AiStrategyTeamsAlertMessage message() {
        return new AiStrategyTeamsAlertMessage(
                "AI_STRATEGY_GENERATION_FAILED",
                "ERROR",
                "test",
                LocalDateTime.of(2026, 8, 30, 14, 0),
                123L,
                null,
                "CASE-2026-123",
                "대체계란 AI 전략",
                7L,
                "이주영",
                1001L,
                "SKU-1001",
                "대체계란",
                10L,
                "압구정본점",
                AiStrategyFailureCategory.GEMINI,
                "STRATEGY_GENERATING",
                "LLM_API_UNAVAILABLE",
                "LLM_API_UNAVAILABLE",
                "Gemini unavailable",
                2,
                3,
                1,
                "사용자 지정",
                "사용자 지정",
                LocalDate.of(2026, 8, 30),
                LocalDate.of(2026, 9, 10),
                "AI_STRATEGY_GENERATION_FAILED:123",
                null,
                null
        );
    }
}
