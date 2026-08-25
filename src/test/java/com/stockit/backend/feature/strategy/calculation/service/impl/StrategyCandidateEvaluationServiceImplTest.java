package com.stockit.backend.feature.strategy.calculation.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stockit.backend.feature.strategy.calculation.candidate.domain.CandidateGenerationResult;
import com.stockit.backend.feature.strategy.calculation.candidate.domain.StrategyCandidate;
import com.stockit.backend.feature.strategy.calculation.candidate.service.StrategyCandidateGenerationService;
import com.stockit.backend.feature.strategy.calculation.domain.BaselineSimulation;
import com.stockit.backend.feature.strategy.calculation.domain.SimulationDetailLevel;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCandidateEvaluationResult;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCandidateSimulation;
import com.stockit.backend.feature.strategy.calculation.engine.BaselineSimulationEngine;
import com.stockit.backend.feature.strategy.calculation.engine.CandidateSimulationException;
import com.stockit.backend.feature.strategy.calculation.engine.StrategyCandidateSimulationEngine;
import com.stockit.backend.feature.strategy.calculation.service.StrategyCalculationContextLoader;

@ExtendWith(MockitoExtension.class)
class StrategyCandidateEvaluationServiceImplTest {

    @Mock
    private StrategyCalculationContextLoader contextLoader;
    @Mock
    private BaselineSimulationEngine baselineEngine;
    @Mock
    private StrategyCandidateGenerationService candidateGenerationService;
    @Mock
    private StrategyCandidateSimulationEngine candidateSimulationEngine;
    @InjectMocks
    private StrategyCandidateEvaluationServiceImpl service;

    @Test
    void isolatesOneCandidateFailureAndKeepsOtherEvaluation() {
        Long strategyCaseId = 12345L;
        StrategyCalculationContext context = mock(StrategyCalculationContext.class);
        BaselineSimulation baseline = mock(BaselineSimulation.class);
        StrategyCandidate accepted = mock(StrategyCandidate.class);
        StrategyCandidate rejected = mock(StrategyCandidate.class);
        StrategyCandidateSimulation simulation = mock(
                StrategyCandidateSimulation.class
        );
        when(accepted.candidateId()).thenReturn("CAND-OK");
        when(rejected.candidateId()).thenReturn("CAND-FAIL");
        when(simulation.candidateId()).thenReturn("CAND-OK");
        when(contextLoader.load(strategyCaseId)).thenReturn(context);
        when(baselineEngine.simulate(context)).thenReturn(baseline);
        when(candidateGenerationService.generate(context)).thenReturn(
                new CandidateGenerationResult(
                        List.of(accepted, rejected),
                        List.of()
                )
        );
        when(candidateSimulationEngine.simulate(
                context,
                accepted,
                baseline,
                SimulationDetailLevel.SUMMARY_ONLY
        )).thenReturn(simulation);
        when(candidateSimulationEngine.simulate(
                context,
                rejected,
                baseline,
                SimulationDetailLevel.SUMMARY_ONLY
        )).thenThrow(new CandidateSimulationException(
                "CANDIDATE_PROJECTED_INVENTORY_UNAVAILABLE",
                "Projected inventory is unavailable"
        ));

        StrategyCandidateEvaluationResult result = service.evaluate(
                strategyCaseId,
                SimulationDetailLevel.SUMMARY_ONLY
        );

        assertThat(result.evaluatedCandidates()).singleElement().satisfies(evaluated ->
                assertThat(evaluated.candidate()).isSameAs(accepted)
        );
        assertThat(result.simulationFailures()).singleElement().satisfies(failure -> {
            assertThat(failure.candidateId()).isEqualTo("CAND-FAIL");
            assertThat(failure.code()).isEqualTo(
                    "CANDIDATE_PROJECTED_INVENTORY_UNAVAILABLE"
            );
        });
    }

    @Test
    void rejectsNullEvaluationResultListsBeforeDefensiveCopy() {
        BaselineSimulation baseline = mock(BaselineSimulation.class);

        assertThatThrownBy(() -> new StrategyCandidateEvaluationResult(
                mock(StrategyCalculationContext.class),
                baseline,
                null,
                List.of(),
                List.of()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("candidate evaluation result is invalid");
    }
}
