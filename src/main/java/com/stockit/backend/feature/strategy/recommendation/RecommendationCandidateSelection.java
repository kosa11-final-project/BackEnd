package com.stockit.backend.feature.strategy.recommendation;

import java.util.List;

import com.stockit.backend.feature.strategy.calculation.domain.StrategyCandidateEvaluationResult;

/** LLM 입력 크기로 축약된, 정렬 순서가 결정적인 후보 목록. */
public record RecommendationCandidateSelection(
        List<StrategyCandidateEvaluationResult.EvaluatedCandidate> candidates
) {
    public RecommendationCandidateSelection {
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("recommendation candidates must not be empty");
        }
        candidates = List.copyOf(candidates);
    }
}
