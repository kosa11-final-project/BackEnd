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
import com.stockit.backend.feature.strategy.observability.AiStrategyGenerationMetrics;
import com.stockit.backend.feature.strategy.observability.AiStrategyGenerationMetrics.Stage;

@Service
public class StrategyCandidateEvaluationServiceImpl
        implements StrategyCandidateEvaluationService {

    private final StrategyCalculationContextLoader contextLoader;
    private final BaselineSimulationEngine baselineEngine;
    private final StrategyCandidateGenerationService candidateGenerationService;
    private final StrategyCandidateSimulationEngine candidateSimulationEngine;
    private final AiStrategyGenerationMetrics metrics;

    public StrategyCandidateEvaluationServiceImpl(
            StrategyCalculationContextLoader contextLoader,
            BaselineSimulationEngine baselineEngine,
            StrategyCandidateGenerationService candidateGenerationService,
            StrategyCandidateSimulationEngine candidateSimulationEngine,
            AiStrategyGenerationMetrics metrics
    ) {
        this.contextLoader = contextLoader;
        this.baselineEngine = baselineEngine;
        this.candidateGenerationService = candidateGenerationService;
        this.candidateSimulationEngine = candidateSimulationEngine;
        this.metrics = metrics;
    }

    @Override
    public StrategyCandidateEvaluationResult evaluate(
            Long strategyCaseId,
            SimulationDetailLevel detailLevel
    ) {
        StrategyCalculationContext context = metrics.measure(
                Stage.CONTEXT_LOAD,
                () -> contextLoader.load(strategyCaseId)
        );
        metrics.recordInput(context);
        BaselineSimulation baseline = metrics.measure(
                Stage.BASELINE_SIMULATION,
                () -> baselineEngine.simulate(context)
        );
        CandidateGenerationResult generated = metrics.measure(
                Stage.CANDIDATE_GENERATION,
                () -> candidateGenerationService.generate(context)
        );
        metrics.recordCandidateCount("generated", generated.candidates().size());
        metrics.recordCandidateCount("generation_excluded", generated.exclusions().size());
        SimulationBatch batch = metrics.measure(
                Stage.CANDIDATE_SIMULATION,
                () -> simulateCandidates(context, baseline, detailLevel, generated)
        );
        metrics.recordCandidateCount("evaluated", batch.evaluated().size());
        metrics.recordCandidateCount("simulation_failed", batch.failures().size());
        return new StrategyCandidateEvaluationResult(
                context,
                baseline,
                batch.evaluated(),
                generated.exclusions(),
                batch.failures()
        );
    }

    private SimulationBatch simulateCandidates(
            StrategyCalculationContext context,
            BaselineSimulation baseline,
            SimulationDetailLevel detailLevel,
            CandidateGenerationResult generated
    ) {
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
        return new SimulationBatch(evaluated, failures);
    }

    private record SimulationBatch(
            List<StrategyCandidateEvaluationResult.EvaluatedCandidate> evaluated,
            List<CandidateSimulationFailure> failures
    ) {
    }
}
