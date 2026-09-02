package com.stockit.backend.feature.strategy.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.domain.StrategyType;

class AiRecommendationQualityEvaluatorTest {

    private final AiRecommendationQualityEvaluator evaluator =
            new AiRecommendationQualityEvaluator();

    @Test
    void measuresEconomicRegretDiversityAndUserPriority() {
        AiRecommendationRequest request = request(List.of(
                candidate("BEST", "FAMILY-A", StrategyType.PRICE_DISCOUNT,
                        10L, "100", 2, 2, true),
                candidate("PRIORITY", "FAMILY-B", StrategyType.RT_TRANSFER,
                        20L, "80", 1, 1, true),
                candidate("OTHER", "FAMILY-C", StrategyType.CHANNEL_EXPANSION,
                        30L, "60", 3, 3, true)
        ));
        AiRecommendationProviderResponse response = response(List.of(
                recommendation("PRIORITY", 1),
                recommendation("BEST", 2),
                recommendation("OTHER", 3)
        ));

        AiRecommendationQualityEvaluation quality = evaluator.evaluate(
                request, response
        );

        assertThat(quality.structuralViolationCount()).isZero();
        assertThat(quality.validRecommendationCount()).isEqualTo(3);
        assertThat(quality.validSelectionRate()).isEqualByComparingTo("1.000000");
        assertThat(quality.distinctStrategyFamilyCount()).isEqualTo(3);
        assertThat(quality.distinctStrategyTypeCount()).isEqualTo(3);
        assertThat(quality.distinctTargetSalesPointCount()).isEqualTo(3);
        assertThat(quality.top1Regret()).isEqualByComparingTo("20");
        assertThat(quality.top1RegretRate()).isEqualByComparingTo("0.200000");
        assertThat(quality.top1StrategyPriorityCompliant()).isTrue();
        assertThat(quality.top1TargetPriorityCompliant()).isTrue();
    }

    @Test
    void reportsStructuralViolationsWithoutFailingEvaluation() {
        AiRecommendationRequest request = request(List.of(
                candidate("A", "FAMILY-A", StrategyType.PRICE_DISCOUNT,
                        10L, "100", 1, 1, false),
                candidate("B", "FAMILY-B", StrategyType.RT_TRANSFER,
                        20L, "80", 2, 2, false),
                candidate("C", "FAMILY-C", StrategyType.CHANNEL_EXPANSION,
                        30L, "60", 3, 3, false)
        ));
        AiRecommendationProviderResponse response = response(List.of(
                recommendation("A", 1),
                recommendation("A", 2),
                recommendation("UNKNOWN", 2)
        ));

        AiRecommendationQualityEvaluation quality = evaluator.evaluate(
                request, response
        );

        assertThat(quality.structuralViolationCount()).isGreaterThanOrEqualTo(2);
        assertThat(quality.validSelectionRate()).isLessThan(BigDecimal.ONE);
        assertThat(quality.distinctStrategyFamilyCount()).isEqualTo(1);
        assertThat(quality.top1StrategyPriorityCompliant()).isNull();
        assertThat(quality.top1TargetPriorityCompliant()).isNull();
    }

    @Test
    void countsSelectedCandidatesThatViolateFixedRequestConstraints() {
        AiRecommendationRequest request = request(List.of(
                candidate("A", "FAMILY-A", StrategyType.PRICE_DISCOUNT,
                        10L, "100", 1, 1, true),
                candidate("B", "FAMILY-B", StrategyType.RT_TRANSFER,
                        20L, "80", 2, 2, true),
                candidate("C", "FAMILY-C", StrategyType.CHANNEL_EXPANSION,
                        30L, "60", 3, 3, true)
        ));
        StrategyCalculationContext.RequestConstraints constraints =
                new StrategyCalculationContext.RequestConstraints(
                        List.of(10L), List.of(StrategyType.PRICE_DISCOUNT),
                        LocalDate.of(2026, 8, 29), LocalDate.of(2026, 9, 5)
                );

        AiRecommendationQualityEvaluation quality = evaluator.evaluate(
                request,
                response(List.of(
                        recommendation("A", 1),
                        recommendation("B", 2),
                        recommendation("C", 3)
                )),
                constraints
        );

        assertThat(quality.fixedConstraintViolationCount()).isEqualTo(4);
    }

    @Test
    void usesAbsoluteBestEffectAsRegretDenominatorForLossCandidates() {
        AiRecommendationRequest request = request(List.of(
                candidate("LESS-LOSS", "FAMILY-A", StrategyType.PRICE_DISCOUNT,
                        10L, "-20", 1, 1, false),
                candidate("MORE-LOSS", "FAMILY-B", StrategyType.RT_TRANSFER,
                        20L, "-30", 2, 2, false),
                candidate("OTHER", "FAMILY-C", StrategyType.CHANNEL_EXPANSION,
                        30L, "-40", 3, 3, false)
        ));

        AiRecommendationQualityEvaluation quality = evaluator.evaluate(
                request,
                response(List.of(
                        recommendation("MORE-LOSS", 1),
                        recommendation("LESS-LOSS", 2),
                        recommendation("OTHER", 3)
                ))
        );

        assertThat(quality.top1Regret()).isEqualByComparingTo("10");
        assertThat(quality.top1RegretRate()).isEqualByComparingTo("0.500000");
    }

    private static AiRecommendationRequest request(
            List<AiRecommendationRequest.CandidateInput> candidates
    ) {
        return new AiRecommendationRequest(
                "quality-test-v1", 1L, 3, 3,
                new AiRecommendationRequest.BaselineInput(
                        decimal("10"), decimal("100"), decimal("20"),
                        decimal("0.2"), null, decimal("90"), decimal("0")
                ),
                candidates
        );
    }

    private static AiRecommendationRequest.CandidateInput candidate(
            String id,
            String family,
            StrategyType type,
            Long targetSalesPointId,
            String netEffect,
            int strategyPriority,
            int targetPriority,
            boolean userPriority
    ) {
        AiRecommendationRequest.PrioritySource source = userPriority
                ? AiRecommendationRequest.PrioritySource.USER
                : AiRecommendationRequest.PrioritySource.AI_DEFAULT;
        return new AiRecommendationRequest.CandidateInput(
                id, family, List.of(type),
                LocalDate.of(2026, 8, 29), LocalDate.of(2026, 9, 5),
                List.of(new AiRecommendationRequest.ActionInput(
                        type, 1L, 10L, 1L, targetSalesPointId,
                        decimal("10"), BigDecimal.ZERO,
                        type == StrategyType.PRICE_DISCOUNT ? decimal("90") : null,
                        type == StrategyType.PRICE_DISCOUNT ? decimal("0.1") : null
                )),
                new AiRecommendationRequest.SummaryInput(
                        decimal("20"), decimal("200"), decimal("50"),
                        decimal("0.25"), 5, decimal("80"), BigDecimal.ZERO,
                        BigDecimal.ZERO, decimal(netEffect)
                ),
                new AiRecommendationRequest.ComparisonInput(
                        decimal("10"), decimal("100"), decimal("30"),
                        decimal("10"), BigDecimal.ZERO, decimal(netEffect)
                ),
                List.of(),
                new AiRecommendationRequest.PreferenceInput(
                        userPriority ? strategyPriority : null, source,
                        userPriority ? targetPriority : null, source,
                        50
                ),
                decimal("10")
        );
    }

    private static AiRecommendationProviderResponse response(
            List<AiRecommendationProviderResponse.Recommendation> recommendations
    ) {
        return new AiRecommendationProviderResponse(
                "interaction", "gemini", 100, 50, recommendations
        );
    }

    private static AiRecommendationProviderResponse.Recommendation recommendation(
            String candidateId,
            int rank
    ) {
        return new AiRecommendationProviderResponse.Recommendation(
                candidateId, rank, "대안", "추천 이유", "장점", "주의사항"
        );
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
