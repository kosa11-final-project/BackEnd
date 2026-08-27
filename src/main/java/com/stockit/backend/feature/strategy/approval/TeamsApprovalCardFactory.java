package com.stockit.backend.feature.strategy.approval;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 선택된 전략을 Teams Adaptive Card 웹후크 계약으로 변환한다. */
@Component
public class TeamsApprovalCardFactory {

    TeamsWebhookRequest create(TeamsApprovalMessage message) {
        TeamsApprovalCardData card = message.cardData();
        List<Map<String, String>> facts = new ArrayList<>();
        addFact(facts, "Case", card.caseName());
        addFact(facts, "대상 상품", card.skuName() + " (" + card.skuCode() + ")");
        addFact(facts, "전략", card.optionName());
        addFact(facts, "전략 타입", String.join(", ", card.strategyTypes()));
        addFact(facts, "대상 판매처", String.join(", ", card.targetSalesPointNames()));
        addFact(facts, "판매 기간", period(card.startDate(), card.endDate()));
        addFact(facts, "적용 수량", quantity(card.targetQuantity()));
        addFact(facts, "할인율", discountRates(card.discountRates()));
        addFact(facts, "전략 판매가", money(card.strategyPrice()));
        addFact(facts, "예상 판매량", quantity(card.expectedSalesQty()));
        addFact(facts, "예상 매출", money(card.expectedRevenue()));
        addFact(facts, "예상 공헌이익", money(card.totalContributionMargin()));
        addFact(facts, "행사 후 예상 잔여 재고", quantity(
                card.expectedRemainingQty()
        ));
        addFact(facts, "요청자", card.requesterName());

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("$schema", "http://adaptivecards.io/schemas/adaptive-card.json");
        content.put("type", "AdaptiveCard");
        content.put("version", "1.4");
        content.put("body", List.of(
                Map.of(
                        "type", "TextBlock",
                        "text", "StockIt AI 전략 검토 요청",
                        "weight", "Bolder",
                        "size", "Medium",
                        "wrap", true
                ),
                Map.of(
                        "type", "FactSet",
                        "facts", facts
                ),
                Map.of(
                        "type", "TextBlock",
                        "text", card.recommendationReason() == null
                                ? "선택된 AI 추천 전략입니다."
                                : card.recommendationReason(),
                        "wrap", true,
                        "spacing", "Medium"
                )
        ));

        return new TeamsWebhookRequest(
                "message",
                message.deliveryKey(),
                message.recipientEmail(),
                List.of(new Attachment(
                        "application/vnd.microsoft.card.adaptive",
                        null,
                        content
                ))
        );
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

    private static String discountRates(
            List<BigDecimal> discountRates
    ) {
        String rates = discountRates.stream()
                .map(rate -> rate.multiply(BigDecimal.valueOf(100))
                        .stripTrailingZeros().toPlainString() + "%")
                .collect(java.util.stream.Collectors.joining(", "));
        return rates.isBlank() ? "해당 없음" : rates;
    }

    private static String period(LocalDate startDate, LocalDate endDate) {
        return endDate == null
                ? startDate.toString()
                : startDate + " ~ " + endDate;
    }

    private static String quantity(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private static String money(BigDecimal value) {
        return value == null
                ? null
                : "₩" + value.setScale(0, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    record TeamsWebhookRequest(
            String type,
            String deliveryKey,
            String recipientEmail,
            List<Attachment> attachments
    ) {
    }

    record Attachment(String contentType, String contentUrl, Object content) {
    }
}
