package com.stockit.backend.feature.demandforecast.alert;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

/** 명시적으로 활성화했을 때만 실제 Teams 운영 채널 Workflow를 호출하는 스모크 테스트입니다. */
@EnabledIfEnvironmentVariable(named = "DEMAND_FORECAST_TEAMS_LIVE_TEST", matches = "true")
class DemandForecastTeamsAlertLiveTest {

    @Test
    void sendsTestCardToRealTeamsChannel() {
        String webhookUrl = System.getenv("DEMAND_FORECAST_TEAMS_WEBHOOK_URL");
        assertThat(webhookUrl)
                .as("DEMAND_FORECAST_TEAMS_WEBHOOK_URL must be set for the live test")
                .isNotBlank();

        DemandForecastTeamsAlertProperties properties =
                new DemandForecastTeamsAlertProperties(
                        true,
                        webhookUrl,
                        null,
                        null,
                        null,
                        "live-test",
                        null
                );
        DemandForecastTeamsAlertSender sender = new DemandForecastTeamsAlertSender(
                RestClient.create(),
                properties,
                new DemandForecastTeamsAlertCardFactory()
        );

        sender.send(new DemandForecastTeamsAlertMessage(
                "[TEST] 수요예측 Teams 알림 연결 확인",
                properties.resolvedEnvironment(),
                999999L,
                LocalDate.now(),
                "LIVE_TEST",
                "TEAMS_ALERT_SMOKE_TEST",
                "실제 장애가 아닌 Teams 채널 Webhook 연결 확인용 테스트 메시지입니다.",
                "live-test-job",
                null,
                Instant.now(),
                "DEMAND_FORECAST:LIVE_TEST:" + Instant.now().toEpochMilli(),
                null
        ));
    }
}
