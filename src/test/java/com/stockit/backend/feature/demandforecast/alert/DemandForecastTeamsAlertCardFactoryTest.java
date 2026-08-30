package com.stockit.backend.feature.demandforecast.alert;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class DemandForecastTeamsAlertCardFactoryTest {

    @Test
    @SuppressWarnings("unchecked")
    void rendersOnlyTheRootCauseAsSmallSubtleText() {
        DemandForecastTeamsAlertMessage message = new DemandForecastTeamsAlertMessage(
                "일일 수요예측 실패",
                "test",
                15L,
                LocalDate.of(2026, 8, 26),
                "FINALIZING",
                "EXPORT_OR_SUBMIT_FAILED",
                """
                        수요예측 파이프라인이 실패했습니다. 원인:
                        ### Error querying database. Cause: first cause
                        ### The error may exist in file [/local/build/Mapper.xml]
                        ### The error may involve mapper.select
                        ### The error occurred while executing a query
                        ### Cause: org.springframework.jdbc.CannotGetJdbcConnectionException: Failed to obtain JDBC Connection
                        """,
                null,
                null,
                Instant.parse("2026-08-26T01:00:00Z"),
                "DEMAND_FORECAST:15:FAILED",
                null
        );

        DemandForecastTeamsAlertCardFactory.TeamsWebhookRequest request =
                new DemandForecastTeamsAlertCardFactory().create(message);
        Map<String, Object> content =
                (Map<String, Object>) request.attachments().get(0).content();
        List<Map<String, Object>> body =
                (List<Map<String, Object>>) content.get("body");

        assertThat(body).hasSize(4);
        assertThat(body.get(2)).containsEntry("text", "오류 상세")
                .containsEntry("size", "Small");
        assertThat(body.get(3))
                .containsEntry("text", "org.springframework.jdbc.CannotGetJdbcConnectionException: Failed to obtain JDBC Connection")
                .containsEntry("size", "Small")
                .containsEntry("isSubtle", true)
                .doesNotContainValue("/local/build/Mapper.xml");
    }
}
