package com.stockit.backend.feature.strategy.recommendation;

import java.math.BigDecimal;

/**
 * 동일한 LLM 후보 집합에서 추천 결과의 경제성·다양성·우선순위 준수를 비교하기 위한
 * 정량 평가 결과다.
 */
public record AiRecommendationQualityEvaluation(
        int candidateCount,
        int recommendationCount,
        int validRecommendationCount,
        int structuralViolationCount,
        int distinctStrategyFamilyCount,
        int distinctStrategyTypeCount,
        int distinctTargetSalesPointCount,
        BigDecimal bestCandidateNetEffect,
        BigDecimal top1NetEffect,
        BigDecimal top1Regret,
        BigDecimal top1RegretRate,
        Boolean top1StrategyPriorityCompliant,
        Boolean top1TargetPriorityCompliant,
        Integer fixedConstraintViolationCount
) {

    public BigDecimal familyDiversityRatio() {
        return ratio(distinctStrategyFamilyCount, recommendationCount);
    }

    public BigDecimal validSelectionRate() {
        if (recommendationCount <= 0) {
            return BigDecimal.ZERO;
        }
        return ratio(validRecommendationCount, recommendationCount);
    }

    private static BigDecimal ratio(int numerator, int denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 6,
                        java.math.RoundingMode.HALF_UP);
    }
}
