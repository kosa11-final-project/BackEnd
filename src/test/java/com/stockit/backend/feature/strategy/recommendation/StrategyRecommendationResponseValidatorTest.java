package com.stockit.backend.feature.strategy.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.stockit.backend.feature.strategy.calculation.domain.BaselineSimulation;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCandidateEvaluationResult;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCandidateSimulation;
import com.stockit.backend.feature.strategy.calculation.candidate.domain.StrategyCandidate;
import com.stockit.backend.feature.strategy.domain.StrategyType;
import com.stockit.backend.feature.strategy.messaging.PermanentStrategyGenerationException;

class StrategyRecommendationResponseValidatorTest {

    private final StrategyRecommendationResponseValidator validator =
            new StrategyRecommendationResponseValidator();

    @Test
    void mapsContiguousUniqueRecommendationsToServerCandidates() {
        var first = evaluated("A", 20L);
        var second = evaluated("B", 30L);
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
        var selection = new RecommendationCandidateSelection(List.of(evaluated("A", 20L)));
        assertThatThrownBy(() -> validator.validateAndMap(
                1L, mock(com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext.class),
                mock(BaselineSimulation.class), selection, request(1, 1),
                response(List.of(item("UNKNOWN", 1)))
        )).isInstanceOf(PermanentStrategyGenerationException.class);
    }

    @Test
    void rejectsTwoCandidatesFromSameStrategyFamily() {
        var selection = new RecommendationCandidateSelection(List.of(
                evaluated("A", 20L), evaluated("B", 20L), evaluated("C", 30L)
        ));

        assertThatThrownBy(() -> validator.validateAndMap(
                1L, mock(com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext.class),
                mock(BaselineSimulation.class), selection, request(2, 3),
                response(List.of(item("A", 1), item("B", 2)))
        )).isInstanceOfSatisfying(
                PermanentStrategyGenerationException.class,
                exception -> assertThat(exception.getMessage())
                        .contains("duplicate strategy families")
        );
    }

    private static StrategyCandidateEvaluationResult.EvaluatedCandidate evaluated(
            String id,
            Long targetSalesPointId
    ) {
        BigDecimal quantity = BigDecimal.TEN;
        StrategyCandidate.Location source = new StrategyCandidate.Location(1L, 10L);
        StrategyCandidate.Location target = new StrategyCandidate.Location(
                1L, targetSalesPointId
        );
        StrategyCandidate.Action action = new StrategyCandidate.Action(
                StrategyType.REALLOCATION,
                source,
                target,
                quantity,
                BigDecimal.ZERO,
                List.of(new StrategyCandidate.LotAllocation(
                        Math.abs((long) id.hashCode()) + 1L,
                        Math.abs((long) id.hashCode()) + 1L,
                        quantity,
                        1
                ))
        );
        StrategyCandidate candidate = new StrategyCandidate(
                id,
                List.of(StrategyType.REALLOCATION),
                LocalDate.of(2026, 8, 25),
                null,
                List.of(action),
                List.of(),
                new StrategyCandidate.Preference(1, 1, 100),
                new StrategyCandidate.MovementEvidence(
                        quantity, quantity, quantity, quantity
                )
        );
        StrategyCandidateSimulation simulation = mock(StrategyCandidateSimulation.class);
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
