package com.stockit.backend.feature.strategy.calculation.candidate.domain;

import com.stockit.backend.feature.strategy.domain.StrategyType;

public record CandidateExclusion(
        StrategyType strategyType,
        Long targetSalesPointId,
        CandidateExclusionReason reason,
        String message
) {
    public CandidateExclusion {
        if (strategyType == null || reason == null || message == null || message.isBlank()) {
            throw new IllegalArgumentException("candidate exclusion is invalid");
        }
    }
}
