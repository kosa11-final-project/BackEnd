package com.stockit.backend.feature.strategy.alert;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/** AI 전략 생성 최종 실패를 Teams용 Adaptive Card로 변환합니다. */
@Component
class AiStrategyTeamsAlertCardFactory {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");

    TeamsWebhookRequest create(AiStrategyTeamsAlertMessage message) {
        List<Map<String, Object>> body = new ArrayList<>();
        body.add(title(message));
        body.add(factSet(failureFacts(message)));
        body.add(factSet(caseFacts(message)));
        body.add(factSet(requestFacts(message)));
        body.add(Map.of(
                "type", "TextBlock",
                "text", "오류 메시지\n" + message.failureMessage(),
                "wrap", true,
                "spacing", "Medium",
                "color", "Attention"
        ));
        body.add(factSet(traceFacts(message)));

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("$schema", "http://adaptivecards.io/schemas/adaptive-card.json");
        content.put("type", "AdaptiveCard");
        content.put("version", "1.4");
        content.put("body", body);
        List<Map<String, String>> actions = actions(message);
        if (!actions.isEmpty()) {
            content.put("actions", actions);
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

    private static Map<String, Object> title(AiStrategyTeamsAlertMessage message) {
        return Map.of(
                "type", "TextBlock",
                "text", "🔴 [ERROR][" + message.environment()
                        + "] AI 전략 생성 최종 실패",
                "weight", "Bolder",
                "size", "Medium",
                "color", "Attention",
                "wrap", true
        );
    }

    private static List<Map<String, String>> failureFacts(
            AiStrategyTeamsAlertMessage message
    ) {
        List<Map<String, String>> facts = new ArrayList<>();
        addFact(facts, "장애 영역", string(message.failureCategory()));
        addFact(facts, "실패 단계", message.failedStage());
        addFact(facts, "최종 처리 코드", message.failureCode());
        if (!java.util.Objects.equals(
                message.failureCode(), message.rootFailureCode()
        )) {
            addFact(facts, "원인 오류 코드", message.rootFailureCode());
        }
        addFact(facts, "발생 시각", occurredAt(message));
        return facts;
    }

    private static List<Map<String, String>> caseFacts(
            AiStrategyTeamsAlertMessage message
    ) {
        List<Map<String, String>> facts = new ArrayList<>();
        addFact(facts, "Case ID", string(message.strategyCaseId()));
        addFact(facts, "Case 코드", message.caseCode());
        addFact(facts, "Case 제목", message.caseName());
        addFact(facts, "재시도 원본 Case ID", string(message.retryParentCaseId()));
        addFact(facts, "요청자", displayWithId(
                message.requesterName(), message.requesterId()
        ));
        addFact(facts, "SKU", displayWithId(message.skuName(), message.skuId()));
        addFact(facts, "SKU 코드", message.skuCode());
        addFact(facts, "현재 재고 보유 판매처", displayWithId(
                message.sourceSalesPointName(), message.sourceSalesPointId()
        ));
        return facts;
    }

    private static List<Map<String, String>> requestFacts(
            AiStrategyTeamsAlertMessage message
    ) {
        List<Map<String, String>> facts = new ArrayList<>();
        addFact(facts, "선택 LOT 수", string(message.requestedLotCount()));
        addFact(facts, "희망 판매처", selection(
                message.salesPointSelectionMode(),
                message.requestedCandidateSalesPointCount()
        ));
        addFact(facts, "전략 타입", selection(
                message.strategyTypeSelectionMode(),
                message.requestedStrategyTypeCount()
        ));
        addFact(facts, "희망 판매 기간", preferredPeriod(message));
        return facts;
    }

    private static List<Map<String, String>> traceFacts(
            AiStrategyTeamsAlertMessage message
    ) {
        List<Map<String, String>> facts = new ArrayList<>();
        addFact(facts, "이벤트", message.eventType());
        addFact(facts, "중복 식별 키", message.deduplicationKey());
        addFact(facts, "로그 검색어", "strategyCaseId=" + message.strategyCaseId());
        return facts;
    }

    private static Map<String, Object> factSet(List<Map<String, String>> facts) {
        return Map.of("type", "FactSet", "facts", facts);
    }

    private static List<Map<String, String>> actions(
            AiStrategyTeamsAlertMessage message
    ) {
        List<Map<String, String>> actions = new ArrayList<>();
        addAction(actions, "AI 전략 Case 확인", message.caseUrl());
        addAction(actions, "로그 확인", message.logUrl());
        return actions;
    }

    private static void addAction(
            List<Map<String, String>> actions,
            String title,
            String url
    ) {
        if (url != null && !url.isBlank()) {
            actions.add(Map.of(
                    "type", "Action.OpenUrl",
                    "title", title,
                    "url", url.trim()
            ));
        }
    }

    private static String occurredAt(AiStrategyTeamsAlertMessage message) {
        return message.occurredAt() == null
                ? null
                : DATE_TIME.format(message.occurredAt().atZone(KST));
    }

    private static String preferredPeriod(AiStrategyTeamsAlertMessage message) {
        if (message.preferredStartDate() == null
                && message.preferredEndDate() == null) {
            return "AI 자동 추천";
        }
        return stringOrDash(message.preferredStartDate())
                + " ~ " + stringOrDash(message.preferredEndDate());
    }

    private static String selection(String mode, Integer count) {
        if (mode == null || mode.isBlank()) {
            return count == null ? null : count + "개";
        }
        return count == null ? mode : mode + " (" + count + "개)";
    }

    private static String displayWithId(String name, Long id) {
        if ((name == null || name.isBlank()) && id == null) {
            return "없음";
        }
        if (name == null || name.isBlank()) {
            return "ID: " + id;
        }
        return id == null ? name : name + " (ID: " + id + ")";
    }

    private static String stringOrDash(Object value) {
        return value == null ? "미지정" : value.toString();
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
