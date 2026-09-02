package com.stockit.backend.feature.strategy.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.stockit.backend.feature.strategy.calculation.candidate.domain.StrategyCandidate;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCandidateEvaluationResult;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCandidateSimulation;
import com.stockit.backend.feature.strategy.domain.StrategyType;

class DeterministicRecommendationFallbackTest {

    private final DeterministicRecommendationFallback fallback =
            new DeterministicRecommendationFallback();

    @Test
    void selectsAtMostOneCandidateFromEachFamilyInPreselectionOrder() {
        RecommendationCandidateSelection selection =
                new RecommendationCandidateSelection(List.of(
                        evaluated("A", 20L),
                        evaluated("A-VARIANT", 20L),
                        evaluated("B", 30L),
                        evaluated("C", 40L),
                        evaluated("D", 50L)
                ));
        AiRecommendationRequest request = new AiRecommendationRequest(
                "v3",
                1L,
                3,
                4,
                mock(AiRecommendationRequest.BaselineInput.class),
                List.of(
                        mock(AiRecommendationRequest.CandidateInput.class),
                        mock(AiRecommendationRequest.CandidateInput.class),
                        mock(AiRecommendationRequest.CandidateInput.class),
                        mock(AiRecommendationRequest.CandidateInput.class),
                        mock(AiRecommendationRequest.CandidateInput.class)
                )
        );

        AiRecommendationProviderResponse response = fallback.create(selection, request);

        assertThat(response.model()).isEqualTo("server-rule-fallback");
        assertThat(response.recommendations())
                .extracting(AiRecommendationProviderResponse.Recommendation::candidateId)
                .containsExactly("A", "B", "C", "D");
        assertThat(response.recommendations())
                .extracting(AiRecommendationProviderResponse.Recommendation::rank)
                .containsExactly(1, 2, 3, 4);
    }

    @Test
    void selectsOneCandidatePerStrategyTypeBeforeFillingRemainingSlots() {
        RecommendationCandidateSelection selection =
                new RecommendationCandidateSelection(List.of(
                        evaluated("MOVE-A", 20L, StrategyType.REALLOCATION),
                        evaluated("MOVE-B", 30L, StrategyType.REALLOCATION),
                        evaluated("DISCOUNT", 10L, StrategyType.PRICE_DISCOUNT),
                        evaluated("EXPANSION", 40L, StrategyType.CHANNEL_EXPANSION)
                ));
        AiRecommendationRequest request = new AiRecommendationRequest(
                "v3", 1L, 3, 3,
                mock(AiRecommendationRequest.BaselineInput.class),
                List.of(
                        mock(AiRecommendationRequest.CandidateInput.class),
                        mock(AiRecommendationRequest.CandidateInput.class),
                        mock(AiRecommendationRequest.CandidateInput.class),
                        mock(AiRecommendationRequest.CandidateInput.class)
                )
        );

        AiRecommendationProviderResponse response = fallback.create(selection, request);

        assertThat(response.recommendations())
                .extracting(AiRecommendationProviderResponse.Recommendation::candidateId)
                .containsExactly("MOVE-A", "DISCOUNT", "EXPANSION");
    }

    private static StrategyCandidateEvaluationResult.EvaluatedCandidate evaluated(
            String id,
            Long targetSalesPointId
    ) {
        return evaluated(id, targetSalesPointId, StrategyType.REALLOCATION);
    }

    private static StrategyCandidateEvaluationResult.EvaluatedCandidate evaluated(
            String id,
            Long targetSalesPointId,
            StrategyType strategyType
    ) {
        StrategyCandidate candidate = mock(StrategyCandidate.class);
        StrategyCandidate.Action action = mock(StrategyCandidate.Action.class);
        StrategyCandidateSimulation simulation = mock(StrategyCandidateSimulation.class);
        when(candidate.candidateId()).thenReturn(id);
        when(candidate.strategyTypes()).thenReturn(List.of(strategyType));
        when(candidate.actions()).thenReturn(List.of(action));
        when(action.actionType()).thenReturn(strategyType);
        when(action.source()).thenReturn(new StrategyCandidate.Location(1L, 10L));
        when(action.target()).thenReturn(
                new StrategyCandidate.Location(1L, targetSalesPointId)
        );
        when(simulation.candidateId()).thenReturn(id);
        return new StrategyCandidateEvaluationResult.EvaluatedCandidate(
                candidate, simulation
        );
    }
}
