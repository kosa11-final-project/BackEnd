package com.stockit.backend.feature.strategy.calculation.candidate.domain;

import java.util.List;

public record CandidateGenerationResult(
        List<StrategyCandidate> candidates,
        List<CandidateExclusion> exclusions
) {
    public CandidateGenerationResult {
        candidates = List.copyOf(candidates);
        exclusions = List.copyOf(exclusions);
    }
}
