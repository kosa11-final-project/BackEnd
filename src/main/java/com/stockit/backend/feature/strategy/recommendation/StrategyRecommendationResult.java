package com.stockit.backend.feature.strategy.recommendation;

import java.util.List;

import com.stockit.backend.feature.strategy.calculation.domain.BaselineSimulation;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCandidateEvaluationResult;

/** 검증된 설명과 서버가 계산한 원본 후보를 결합한 추천 결과. */
public record StrategyRecommendationResult(
        Long strategyCaseId,
        StrategyCalculationContext calculationContext,
        BaselineSimulation baselineSimulation,
        List<RecommendedOption> options,
        NoRecommendation noRecommendation,
        ProviderMetadata providerMetadata
) {
    public StrategyRecommendationResult {
        if (strategyCaseId == null || calculationContext == null
                || baselineSimulation == null || options == null) {
            throw new IllegalArgumentException("strategy recommendation result is invalid");
        }
        options = List.copyOf(options);
        boolean recommended = !options.isEmpty();
        if (recommended == (noRecommendation != null)
                || recommended != (providerMetadata != null)) {
            throw new IllegalArgumentException("strategy recommendation outcome is invalid");
        }
    }

    public static StrategyRecommendationResult noRecommendation(
            Long strategyCaseId,
            StrategyCalculationContext calculationContext,
            BaselineSimulation baselineSimulation,
            String code,
            String message
    ) {
        return new StrategyRecommendationResult(
                strategyCaseId, calculationContext, baselineSimulation, List.of(),
                new NoRecommendation(code, message), null
        );
    }

    public record RecommendedOption(
            int rank,
            String optionName,
            String recommendationReason,
            String advantage,
            String caution,
            StrategyCandidateEvaluationResult.EvaluatedCandidate evaluatedCandidate
    ) {
    }

    public record ProviderMetadata(
            String interactionId,
            String model,
            Integer inputTokens,
            Integer outputTokens
    ) {
    }

    public record NoRecommendation(
            String code,
            String message
    ) {
        public NoRecommendation {
            if (code == null || code.isBlank() || message == null || message.isBlank()) {
                throw new IllegalArgumentException("no recommendation reason is invalid");
            }
        }
    }
}
