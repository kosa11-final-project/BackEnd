package com.stockit.backend.feature.strategy.calculation.service.impl;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.stockit.backend.feature.strategy.calculation.candidate.domain.CandidateGenerationResult;
import com.stockit.backend.feature.strategy.calculation.candidate.domain.StrategyCandidate;
import com.stockit.backend.feature.strategy.calculation.candidate.service.StrategyCandidateGenerationService;
import com.stockit.backend.feature.strategy.calculation.domain.BaselineSimulation;
import com.stockit.backend.feature.strategy.calculation.domain.CandidateSimulationFailure;
import com.stockit.backend.feature.strategy.calculation.domain.SimulationDetailLevel;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCandidateEvaluationResult;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCandidateSimulation;
import com.stockit.backend.feature.strategy.calculation.engine.BaselineSimulationEngine;
import com.stockit.backend.feature.strategy.calculation.engine.CandidateSimulationException;
import com.stockit.backend.feature.strategy.calculation.engine.StrategyCandidateSimulationEngine;
import com.stockit.backend.feature.strategy.calculation.service.StrategyCalculationContextLoader;
import com.stockit.backend.feature.strategy.calculation.service.StrategyCandidateEvaluationService;

@Service
public class StrategyCandidateEvaluationServiceImpl
        implements StrategyCandidateEvaluationService {

    private final StrategyCalculationContextLoader contextLoader;
    private final BaselineSimulationEngine baselineEngine;
    private final StrategyCandidateGenerationService candidateGenerationService;
    private final StrategyCandidateSimulationEngine candidateSimulationEngine;

    public StrategyCandidateEvaluationServiceImpl(
            StrategyCalculationContextLoader contextLoader,
            BaselineSimulationEngine baselineEngine,
            StrategyCandidateGenerationService candidateGenerationService,
            StrategyCandidateSimulationEngine candidateSimulationEngine
    ) {
        this.contextLoader = contextLoader;
        this.baselineEngine = baselineEngine;
        this.candidateGenerationService = candidateGenerationService;
        this.candidateSimulationEngine = candidateSimulationEngine;
    }

    @Override
    public StrategyCandidateEvaluationResult evaluate(
            Long strategyCaseId,
            SimulationDetailLevel detailLevel
    ) {
        StrategyCalculationContext context = contextLoader.load(strategyCaseId);
        BaselineSimulation baseline = baselineEngine.simulate(context);
        CandidateGenerationResult generated = candidateGenerationService.generate(context);
        List<StrategyCandidateEvaluationResult.EvaluatedCandidate> evaluated =
                new ArrayList<>();
        List<CandidateSimulationFailure> failures = new ArrayList<>();

        for (StrategyCandidate candidate : generated.candidates()) {
            try {
                StrategyCandidateSimulation simulation =
                        candidateSimulationEngine.simulate(
                                context,
                                candidate,
                                baseline,
                                detailLevel
                        );
                evaluated.add(
                        new StrategyCandidateEvaluationResult.EvaluatedCandidate(
                                candidate,
                                simulation
                        )
                );
            } catch (CandidateSimulationException exception) {
                failures.add(new CandidateSimulationFailure(
                        candidate.candidateId(),
                        exception.getCode(),
                        exception.getMessage()
                ));
            }
        }
        return new StrategyCandidateEvaluationResult(
                context,
                baseline,
                evaluated,
                generated.exclusions(),
                failures
        );
    }
}
