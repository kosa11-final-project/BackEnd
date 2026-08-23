package com.stockit.backend.feature.strategy.calculation.domain;

import java.util.List;

import com.stockit.backend.feature.strategy.calculation.candidate.domain.CandidateExclusion;
import com.stockit.backend.feature.strategy.calculation.candidate.domain.StrategyCandidate;

/** 무전략 기준과 후보 생성·시뮬레이션 결과를 LLM 전처리 단계에 전달한다. */
public record StrategyCandidateEvaluationResult(
        BaselineSimulation baselineSimulation,
        List<EvaluatedCandidate> evaluatedCandidates,
        List<CandidateExclusion> generationExclusions,
        List<CandidateSimulationFailure> simulationFailures
) {
    public StrategyCandidateEvaluationResult {
        if (baselineSimulation == null
                || evaluatedCandidates == null
                || generationExclusions == null
                || simulationFailures == null) {
            throw new IllegalArgumentException("candidate evaluation result is invalid");
        }
        evaluatedCandidates = List.copyOf(evaluatedCandidates);
        generationExclusions = List.copyOf(generationExclusions);
        simulationFailures = List.copyOf(simulationFailures);
    }

    public record EvaluatedCandidate(
            StrategyCandidate candidate,
            StrategyCandidateSimulation simulation
    ) {
        public EvaluatedCandidate {
            if (candidate == null || simulation == null
                    || !candidate.candidateId().equals(simulation.candidateId())) {
                throw new IllegalArgumentException("evaluated candidate is invalid");
            }
        }
    }
}
