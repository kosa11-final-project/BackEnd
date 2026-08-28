package com.stockit.backend.feature.strategy.service;

import com.stockit.backend.feature.strategy.recommendation.RecommendationExecutionPolicy;

public interface StrategyRecommendationStageProcessor {
    void process(
            Long strategyCaseId,
            RecommendationExecutionPolicy executionPolicy
    );

    default void process(Long strategyCaseId) {
        process(
                strategyCaseId,
                RecommendationExecutionPolicy.retryTransientLlmFailure()
        );
    }
}
