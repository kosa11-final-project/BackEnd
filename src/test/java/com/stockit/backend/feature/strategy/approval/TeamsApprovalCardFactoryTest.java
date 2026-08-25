package com.stockit.backend.feature.strategy.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCandidateSimulation;
import com.stockit.backend.feature.strategy.domain.StrategyType;
import com.stockit.backend.feature.strategy.result.StrategyGenerationResult;

@ExtendWith(MockitoExtension.class)
class TeamsApprovalCardFactoryTest {

    @Mock private StrategyGenerationResult.Option option;
    @Mock private StrategyGenerationResult.Candidate candidate;
    @Mock private StrategyCandidateSimulation simulation;
    @Mock private StrategyCandidateSimulation.Summary summary;
    @Mock private StrategyCalculationContext context;
    @Mock private ResolvedStrategySelection resolved;

    @Test
    void createsPersonalChatWebhookPayloadWithAdaptiveCard() {
        StrategyGenerationResult.Action action = new StrategyGenerationResult.Action(
                StrategyType.PRICE_DISCOUNT,
                null,
                10L,
                null,
                20L,
                decimal("12"),
                BigDecimal.ZERO,
                decimal("8500"),
                decimal("0.15"),
                List.of()
        );
        when(option.optionName()).thenReturn("15% 할인 전략");
        when(option.recommendationReason()).thenReturn("예상 소진을 단축합니다.");
        when(option.candidate()).thenReturn(candidate);
        when(option.simulation()).thenReturn(simulation);
        when(candidate.strategyTypes()).thenReturn(List.of(StrategyType.PRICE_DISCOUNT));
        when(candidate.actions()).thenReturn(List.of(action));
        when(candidate.startDate()).thenReturn(LocalDate.of(2026, 8, 25));
        when(simulation.summary()).thenReturn(summary);
        when(summary.expectedSalesQty()).thenReturn(decimal("10"));
        when(summary.expectedRevenue()).thenReturn(decimal("85000"));
        when(summary.totalContributionMargin()).thenReturn(decimal("18000"));
        when(summary.expectedRemainingQty()).thenReturn(decimal("2"));
        when(context.salesPoints()).thenReturn(Map.of(
                20L,
                new StrategyCalculationContext.SalesPoint(
                        20L, "DEPT_20", "목표 판매처", BigDecimal.ZERO,
                        false, null, Map.of(), List.of()
                )
        ));
        when(resolved.option()).thenReturn(option);
        when(resolved.calculationContext()).thenReturn(context);
        when(resolved.targetQuantity()).thenReturn(decimal("12"));
        when(resolved.evaluationEndDate()).thenReturn(
                LocalDate.of(2026, 8, 31)
        );

        var request = new TeamsApprovalCardFactory().create(
                new TeamsApprovalMessage(
                        "reviewer@stockit.test",
                        123L,
                        "테스트 Case",
                        "SKU-1",
                        "테스트 상품",
                        "요청자",
                        resolved
                )
        );

        assertThat(request.type()).isEqualTo("message");
        assertThat(request.recipientEmail()).isEqualTo("reviewer@stockit.test");
        assertThat(request.attachments()).singleElement()
                .satisfies(attachment -> {
                    assertThat(attachment.contentType())
                            .isEqualTo("application/vnd.microsoft.card.adaptive");
                    assertThat(attachment.content()).isInstanceOf(Map.class);
                });
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
