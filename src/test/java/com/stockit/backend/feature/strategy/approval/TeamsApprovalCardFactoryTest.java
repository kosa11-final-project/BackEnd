package com.stockit.backend.feature.strategy.approval;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class TeamsApprovalCardFactoryTest {

    @Test
    void createsPersonalChatWebhookPayloadWithAdaptiveCard() {
        TeamsApprovalCardData card = new TeamsApprovalCardData(
                "테스트 Case", "SKU-1", "테스트 상품", "요청자",
                "15% 할인 전략", "예상 소진을 단축합니다.",
                List.of("PRICE_DISCOUNT"), List.of("목표 판매처"),
                LocalDate.of(2026, 8, 25),
                LocalDate.of(2026, 8, 31),
                decimal("12"), List.of(decimal("0.15")),
                decimal("8500"), decimal("10"), decimal("85000"),
                decimal("18000"), decimal("2")
        );

        var request = new TeamsApprovalCardFactory().create(
                new TeamsApprovalMessage(
                        701L,
                        "AI_STRATEGY_REVIEW:701",
                        "reviewer@stockit.test",
                        card
                )
        );

        assertThat(request.type()).isEqualTo("message");
        assertThat(request.deliveryKey()).isEqualTo("AI_STRATEGY_REVIEW:701");
        assertThat(request.recipientEmail()).isEqualTo("reviewer@stockit.test");
        assertThat(request.attachments()).singleElement()
                .satisfies(attachment -> {
                    assertThat(attachment.contentType())
                            .isEqualTo("application/vnd.microsoft.card.adaptive");
                    assertThat(attachment.content()).isInstanceOf(Map.class);
                    assertThat(cardFacts(attachment.content())).contains(
                            Map.of("title", "적용 수량", "value", "12"),
                            Map.of("title", "전략 판매가", "value", "₩8500"),
                            Map.of(
                                    "title", "판매 기간",
                                    "value", "2026-08-25 ~ 2026-08-31"
                            )
                    );
                });
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, String>> cardFacts(Object content) {
        Map<String, Object> card = (Map<String, Object>) content;
        List<Map<String, Object>> body =
                (List<Map<String, Object>>) card.get("body");
        return body.stream()
                .filter(element -> "FactSet".equals(element.get("type")))
                .map(element ->
                        (List<Map<String, String>>) element.get("facts"))
                .findFirst()
                .orElseThrow();
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
