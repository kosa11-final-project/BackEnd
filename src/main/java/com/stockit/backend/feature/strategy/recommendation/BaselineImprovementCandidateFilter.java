package com.stockit.backend.feature.strategy.recommendation;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Component;

import com.stockit.backend.feature.strategy.calculation.domain.StrategyCandidateEvaluationResult;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCandidateSimulation;

/** 무전략 기준보다 측정 가능한 개선점이 하나도 없는 후보를 LLM 입력에서 제외한다. */
@Component
public class BaselineImprovementCandidateFilter {

    public List<StrategyCandidateEvaluationResult.EvaluatedCandidate> filter(
            List<StrategyCandidateEvaluationResult.EvaluatedCandidate> candidates
    ) {
        if (candidates == null) {
            throw new IllegalArgumentException("evaluated candidates must not be null");
        }
        return candidates.stream().filter(this::improvesAnyMeasuredOutcome).toList();
    }

    boolean improvesAnyMeasuredOutcome(
            StrategyCandidateEvaluationResult.EvaluatedCandidate candidate
    ) {
        if (candidate == null || candidate.simulation() == null
                || candidate.simulation().comparisonToBaseline() == null) {
            throw new IllegalArgumentException("evaluated candidate is invalid");
        }
        StrategyCandidateSimulation.ComparisonToBaseline comparison =
                candidate.simulation().comparisonToBaseline();
        return positive(comparison.salesQtyDelta())
                || positive(comparison.remainingQtyReduction())
                || positive(comparison.disposalQtyReduction())
                || positive(comparison.netEffect());
    }

    private static boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }
}
