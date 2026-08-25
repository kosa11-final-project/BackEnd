package com.stockit.backend.feature.strategy.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stockit.backend.feature.strategy.calculation.candidate.domain.StrategyCandidate;
import com.stockit.backend.feature.strategy.calculation.domain.BaselineSimulation;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCandidateEvaluationResult;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCandidateSimulation;

@ExtendWith(MockitoExtension.class)
class StrategyRecommendationServiceTest {

    @Mock private BaselineImprovementCandidateFilter baselineImprovementFilter;
    @Mock private RecommendationCandidatePreselector preselector;
    @Mock private AiRecommendationRequestFactory requestFactory;
    @Mock private AiRecommendationProvider provider;
    @Mock private StrategyRecommendationResponseValidator validator;

    private StrategyRecommendationService service;

    @BeforeEach
    void setUp() {
        service = new StrategyRecommendationService(
                baselineImprovementFilter,
                preselector,
                requestFactory,
                provider,
                validator
        );
    }

    @Test
    void returnsCurrentStatePreferredWithoutCallingLlmWhenNoCandidateImprovesBaseline() {
        StrategyCalculationContext context = mock(StrategyCalculationContext.class);
        BaselineSimulation baseline = mock(BaselineSimulation.class);
        var evaluated = evaluated("LOSS");
        StrategyCandidateEvaluationResult evaluation =
                new StrategyCandidateEvaluationResult(
                        context, baseline, List.of(evaluated), List.of(), List.of()
                );
        when(baselineImprovementFilter.filter(evaluation.evaluatedCandidates()))
                .thenReturn(List.of());

        StrategyRecommendationResult result = service.recommend(1L, evaluation);

        assertThat(result.options()).isEmpty();
        assertThat(result.noRecommendation().code())
                .isEqualTo("CURRENT_STATE_PREFERRED");
        assertThat(result.providerMetadata()).isNull();
        verify(preselector, never()).select(any());
        verify(requestFactory, never()).create(any(), any(), any(), any());
        verify(provider, never()).recommend(any());
        verify(validator, never()).validateAndMap(any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsEvaluationWhenNoCandidateWasSuccessfullySimulated() {
        StrategyCandidateEvaluationResult evaluation =
                new StrategyCandidateEvaluationResult(
                        mock(StrategyCalculationContext.class),
                        mock(BaselineSimulation.class),
                        List.of(),
                        List.of(),
                        List.of()
                );

        assertThatThrownBy(() -> service.recommend(1L, evaluation))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("no evaluated candidate is available");

        verify(baselineImprovementFilter, never()).filter(any());
        verify(provider, never()).recommend(any());
    }

    private static StrategyCandidateEvaluationResult.EvaluatedCandidate evaluated(
            String id
    ) {
        StrategyCandidate candidate = mock(StrategyCandidate.class);
        StrategyCandidateSimulation simulation = mock(StrategyCandidateSimulation.class);
        when(candidate.candidateId()).thenReturn(id);
        when(candidate.strategyTypes()).thenReturn(
                List.of(com.stockit.backend.feature.strategy.domain.StrategyType.PRICE_DISCOUNT)
        );
        when(simulation.candidateId()).thenReturn(id);
        return new StrategyCandidateEvaluationResult.EvaluatedCandidate(
                candidate, simulation
        );
    }
}
