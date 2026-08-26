package com.stockit.backend.feature.demandforecast.alert;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** 외부 Teams 채널 호출 없이 Webhook 요청 계약을 검증합니다. */
class DemandForecastTeamsAlertLiveTest {
    private static final String MOCK_WEBHOOK_URL = "https://example.test/teams-alert";

    @Test
    void sendsTestCardToMockTeamsEndpoint() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DemandForecastTeamsAlertProperties properties =
                new DemandForecastTeamsAlertProperties(
                        true,
                        MOCK_WEBHOOK_URL,
                        null,
                        null,
                        Duration.ofMinutes(10),
                        "test",
                        null
                );
        DemandForecastTeamsAlertSender sender = new DemandForecastTeamsAlertSender(
                builder.build(),
                properties,
                new DemandForecastTeamsAlertCardFactory()
        );
        server.expect(requestTo(MOCK_WEBHOOK_URL))
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
                                "version": "1.4",
                                "body": [
                                  {
                                    "type": "TextBlock",
                                    "text": "🔴 [TEST] 수요예측 Teams 알림 요청 계약"
                                  },
                                  {
                                    "type": "FactSet",
                                    "facts": [
                                      {"title": "환경", "value": "test"},
                                      {"title": "Forecast Run ID", "value": "999999"},
                                      {"title": "예측 기준일", "value": "2026-08-10"},
                                      {"title": "실패 단계", "value": "CONTRACT_TEST"},
                                      {"title": "오류 코드", "value": "TEAMS_ALERT_TEST"},
                                      {"title": "Azure Job ID", "value": "test-job"},
                                      {"title": "발생 시각", "value": "2026-08-26 10:02:03 KST"},
                                      {
                                        "title": "중복 방지 키",
                                        "value": "DEMAND_FORECAST:TEST:999999"
                                      }
                                    ]
                                  },
                                  {
                                    "type": "TextBlock",
                                    "text": "외부 채널을 호출하지 않는 요청 계약 테스트입니다."
                                  }
                                ]
                              }
                            }
                          ]
                        }
                        """, JsonCompareMode.LENIENT))
                .andRespond(withSuccess());

        sender.send(new DemandForecastTeamsAlertMessage(
                "[TEST] 수요예측 Teams 알림 요청 계약",
                properties.resolvedEnvironment(),
                999999L,
                LocalDate.of(2026, 8, 10),
                "CONTRACT_TEST",
                "TEAMS_ALERT_TEST",
                "외부 채널을 호출하지 않는 요청 계약 테스트입니다.",
                "test-job",
                null,
                Instant.parse("2026-08-26T01:02:03Z"),
                "DEMAND_FORECAST:TEST:999999",
                null
        ));

        server.verify();
    }
}
