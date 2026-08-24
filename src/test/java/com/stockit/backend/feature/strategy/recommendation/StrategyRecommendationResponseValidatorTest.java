package com.stockit.backend.feature.strategy.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.stockit.backend.feature.strategy.calculation.domain.BaselineSimulation;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCandidateEvaluationResult;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCandidateSimulation;
import com.stockit.backend.feature.strategy.calculation.candidate.domain.StrategyCandidate;
import com.stockit.backend.feature.strategy.messaging.PermanentStrategyGenerationException;

class StrategyRecommendationResponseValidatorTest {

    private final StrategyRecommendationResponseValidator validator =
            new StrategyRecommendationResponseValidator();

    @Test
    void mapsContiguousUniqueRecommendationsToServerCandidates() {
        var first = evaluated("A");
        var second = evaluated("B");
        var selection = new RecommendationCandidateSelection(List.of(first, second));
        AiRecommendationRequest request = request(2, 2);
        AiRecommendationProviderResponse response = response(List.of(
                item("B", 2), item("A", 1)
        ));

        StrategyRecommendationResult result = validator.validateAndMap(
                1L, mock(com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext.class),
                mock(BaselineSimulation.class), selection, request, response
        );

        assertThat(result.options()).extracting(option ->
                option.evaluatedCandidate().candidate().candidateId())
                .containsExactly("A", "B");
    }

    @Test
    void rejectsCandidateOutsidePreselectedPool() {
        var selection = new RecommendationCandidateSelection(List.of(evaluated("A")));
        assertThatThrownBy(() -> validator.validateAndMap(
                1L, mock(com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext.class),
                mock(BaselineSimulation.class), selection, request(1, 1),
                response(List.of(item("UNKNOWN", 1)))
        )).isInstanceOf(PermanentStrategyGenerationException.class);
    }

    private static StrategyCandidateEvaluationResult.EvaluatedCandidate evaluated(String id) {
        StrategyCandidate candidate = mock(StrategyCandidate.class);
        StrategyCandidateSimulation simulation = mock(StrategyCandidateSimulation.class);
        when(candidate.candidateId()).thenReturn(id);
        when(simulation.candidateId()).thenReturn(id);
        return new StrategyCandidateEvaluationResult.EvaluatedCandidate(candidate, simulation);
    }

    private static AiRecommendationRequest request(int min, int max) {
        return new AiRecommendationRequest(
                "v1", 1L, min, max,
                new AiRecommendationRequest.BaselineInput(
                        null, null, null, null, null, null, null
                ),
                java.util.stream.IntStream.range(0, max)
                        .mapToObj(index -> mock(AiRecommendationRequest.CandidateInput.class))
                        .toList()
        );
    }

    private static AiRecommendationProviderResponse response(
            List<AiRecommendationProviderResponse.Recommendation> values
    ) {
        return new AiRecommendationProviderResponse("i1", "m1", 10, 20, values);
    }

    private static AiRecommendationProviderResponse.Recommendation item(
            String id, int rank
    ) {
        return new AiRecommendationProviderResponse.Recommendation(
                id, rank, "전략 " + rank, "추천 이유", "장점", "주의사항"
        );
    }
}
