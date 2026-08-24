package com.stockit.backend.feature.strategy.approval;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.result.StrategyGenerationResult;

/** 선택된 전략을 Teams Adaptive Card 웹후크 계약으로 변환한다. */
@Component
public class TeamsApprovalCardFactory {

    TeamsWebhookRequest create(TeamsApprovalMessage message) {
        StrategyGenerationResult.Option option = message.option();
        StrategyGenerationResult.Candidate candidate = option.candidate();
        List<Map<String, String>> facts = new ArrayList<>();
        addFact(facts, "Case", message.caseName());
        addFact(facts, "대상 상품", message.skuName() + " (" + message.skuCode() + ")");
        addFact(facts, "전략", option.optionName());
        addFact(facts, "전략 타입", candidate.strategyTypes().stream()
                .map(Enum::name)
                .collect(Collectors.joining(", ")));
        addFact(facts, "대상 판매처", targetSalesPoints(candidate, message.calculationContext()));
        addFact(facts, "판매 기간", period(candidate.startDate(), candidate.endDate()));
        addFact(facts, "적용 수량", quantity(totalActionQuantity(candidate)));
        addFact(facts, "할인율", discountRates(candidate));
        addFact(facts, "예상 판매량", quantity(option.simulation().summary().expectedSalesQty()));
        addFact(facts, "예상 매출", money(option.simulation().summary().expectedRevenue()));
        addFact(facts, "예상 공헌이익", money(
                option.simulation().summary().totalContributionMargin()
        ));
        addFact(facts, "행사 후 예상 잔여 재고", quantity(
                option.simulation().summary().expectedRemainingQty()
        ));
        addFact(facts, "요청자", message.requesterName());

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
                        "text", option.recommendationReason() == null
                                ? "선택된 AI 추천 전략입니다."
                                : option.recommendationReason(),
                        "wrap", true,
                        "spacing", "Medium"
                )
        ));

        return new TeamsWebhookRequest(
                "message",
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

    private static String targetSalesPoints(
            StrategyGenerationResult.Candidate candidate,
            StrategyCalculationContext context
    ) {
        return candidate.actions().stream()
                .map(StrategyGenerationResult.Action::targetSalesPointId)
                .filter(Objects::nonNull)
                .distinct()
                .map(id -> {
                    StrategyCalculationContext.SalesPoint point =
                            context.salesPoints().get(id);
                    return point == null ? String.valueOf(id) : point.salesPointName();
                })
                .collect(Collectors.joining(", "));
    }

    private static BigDecimal totalActionQuantity(
            StrategyGenerationResult.Candidate candidate
    ) {
        return candidate.actions().stream()
                .map(StrategyGenerationResult.Action::actionQuantity)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String discountRates(
            StrategyGenerationResult.Candidate candidate
    ) {
        String rates = candidate.actions().stream()
                .map(StrategyGenerationResult.Action::discountRate)
                .filter(Objects::nonNull)
                .distinct()
                .map(rate -> rate.multiply(BigDecimal.valueOf(100))
                        .stripTrailingZeros().toPlainString() + "%")
                .collect(Collectors.joining(", "));
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
            String recipientEmail,
            List<Attachment> attachments
    ) {
    }

    record Attachment(String contentType, String contentUrl, Object content) {
    }
}
