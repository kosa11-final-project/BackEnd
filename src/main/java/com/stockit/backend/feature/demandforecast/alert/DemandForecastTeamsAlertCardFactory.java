package com.stockit.backend.feature.demandforecast.alert;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/** 수요예측 장애를 Teams 채널용 Adaptive Card로 변환합니다. */
@Component
class DemandForecastTeamsAlertCardFactory {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter OCCURRED_AT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");

    TeamsWebhookRequest create(DemandForecastTeamsAlertMessage message) {
        List<Map<String, String>> facts = new ArrayList<>();
        addFact(facts, "환경", message.environment());
        addFact(facts, "Forecast Run ID", string(message.forecastRunId()));
        addFact(facts, "예측 기준일", string(message.baseDate()));
        addFact(facts, "실패 단계", message.failedStage());
        addFact(facts, "오류 코드", message.errorCode());
        addFact(facts, "Azure Job ID", message.azureJobId());
        addFact(facts, "스케줄러", message.schedulerName());
        addFact(facts, "발생 시각", occurredAt(message));
        addFact(facts, "중복 방지 키", message.deduplicationKey());

        List<Map<String, Object>> body = new ArrayList<>();
        body.add(Map.of(
                "type", "TextBlock",
                "text", "🔴 " + message.title(),
                "weight", "Bolder",
                "size", "Medium",
                "color", "Attention",
                "wrap", true
        ));
        body.add(Map.of("type", "FactSet", "facts", facts));
        if (message.errorMessage() != null && !message.errorMessage().isBlank()) {
            body.add(Map.of(
                    "type", "TextBlock",
                    "text", message.errorMessage(),
                    "wrap", true,
                    "spacing", "Medium"
            ));
        }

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("$schema", "http://adaptivecards.io/schemas/adaptive-card.json");
        content.put("type", "AdaptiveCard");
        content.put("version", "1.4");
        content.put("body", body);
        if (message.dashboardUrl() != null && !message.dashboardUrl().isBlank()) {
            content.put("actions", List.of(Map.of(
                    "type", "Action.OpenUrl",
                    "title", "관리자 화면 열기",
                    "url", message.dashboardUrl().trim()
            )));
        }

        return new TeamsWebhookRequest(
                "message",
                List.of(new Attachment(
                        "application/vnd.microsoft.card.adaptive",
                        null,
                        content
                ))
        );
    }

    private static String occurredAt(DemandForecastTeamsAlertMessage message) {
        return message.occurredAt() == null
                ? null
                : OCCURRED_AT.format(message.occurredAt().atZone(KST));
    }

    private static String string(Object value) {
        return value == null ? null : value.toString();
    }

    private static void addFact(
            List<Map<String, String>> facts,
            String title,
            String value
    ) {
        if (value != null && !value.isBlank()) {
            facts.add(Map.of("title", title, "value", value));
        }
    }

    record TeamsWebhookRequest(String type, List<Attachment> attachments) {
    }

    record Attachment(String contentType, String contentUrl, Object content) {
    }
}
