package com.stockit.backend.feature.strategy.recommendation;

import org.springframework.stereotype.Component;

import com.stockit.backend.feature.strategy.calculation.candidate.domain.CandidateExclusionImpact;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCandidateEvaluationResult;

/** 후보가 없다는 사실을 정상적인 실행 불가와 계산 입력 부족으로 구분한다. */
@Component
public class StrategyCandidateEvaluationClassifier {

    public CandidateEvaluationDisposition classify(
            StrategyCandidateEvaluationResult evaluation
    ) {
        if (evaluation == null) {
            throw new IllegalArgumentException("candidate evaluation is required");
        }
        if (!evaluation.evaluatedCandidates().isEmpty()) {
            return CandidateEvaluationDisposition.RECOMMENDABLE;
        }
        if (!evaluation.simulationFailures().isEmpty()) {
            return CandidateEvaluationDisposition.SIMULATION_FAILED;
        }
        boolean inputUnavailable = evaluation.generationExclusions().stream()
                .anyMatch(exclusion -> exclusion.reason().impact()
                        == CandidateExclusionImpact.INPUT_UNAVAILABLE);
        if (inputUnavailable) {
            return CandidateEvaluationDisposition.INPUT_DATA_UNAVAILABLE;
        }
        boolean businessIneligible = evaluation.generationExclusions().stream()
                .anyMatch(exclusion -> exclusion.reason().impact()
                        == CandidateExclusionImpact.BUSINESS_INELIGIBLE);
        return businessIneligible
                ? CandidateEvaluationDisposition.NO_EXECUTABLE_STRATEGY
                : CandidateEvaluationDisposition.INVALID_EVALUATION_RESULT;
    }
}
