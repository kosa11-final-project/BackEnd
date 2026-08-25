package com.stockit.backend.feature.strategy.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.stockit.backend.feature.strategy.calculation.candidate.domain.StrategyCandidate;
import com.stockit.backend.feature.strategy.calculation.domain.BaselineSimulation;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCandidateEvaluationResult;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCandidateSimulation;
import com.stockit.backend.feature.strategy.domain.StrategyType;

class AiRecommendationRequestFactoryTest {

    private final AiRecommendationRequestFactory factory =
            new AiRecommendationRequestFactory();

    @Test
    void marksGeneratedOrderAsAiDefaultWhenUserLeftPreferencesEmpty() {
        AiRecommendationRequest request = factory.create(
                1L,
                baseline(),
                selection(),
                new StrategyCalculationContext.RequestConstraints(
                        List.of(), List.of(), null, null
                )
        );

        AiRecommendationRequest.PreferenceInput preference =
                request.candidates().get(0).preference();
        assertThat(request.schemaVersion()).isEqualTo("ai-strategy-recommendation-v2");
        assertThat(request.minimumRecommendationCount()).isEqualTo(1);
        assertThat(request.maximumRecommendationCount()).isEqualTo(1);
        assertThat(preference.strategyPriority()).isNull();
        assertThat(preference.strategyPrioritySource())
                .isEqualTo(AiRecommendationRequest.PrioritySource.AI_DEFAULT);
        assertThat(preference.targetPriority()).isNull();
        assertThat(preference.targetPrioritySource())
                .isEqualTo(AiRecommendationRequest.PrioritySource.AI_DEFAULT);
    }

    @Test
    void preservesOnlyPrioritiesExplicitlyChosenByUser() {
        AiRecommendationRequest request = factory.create(
                1L,
                baseline(),
                selection(),
                new StrategyCalculationContext.RequestConstraints(
                        List.of(20L),
                        List.of(StrategyType.REALLOCATION),
                        null,
                        null
                )
        );

        AiRecommendationRequest.PreferenceInput preference =
                request.candidates().get(0).preference();
        assertThat(preference.strategyPriority()).isEqualTo(1);
        assertThat(preference.strategyPrioritySource())
                .isEqualTo(AiRecommendationRequest.PrioritySource.USER);
        assertThat(preference.targetPriority()).isEqualTo(1);
        assertThat(preference.targetPrioritySource())
                .isEqualTo(AiRecommendationRequest.PrioritySource.USER);
    }

    private static RecommendationCandidateSelection selection() {
        StrategyCandidate candidate = candidate();
        StrategyCandidateSimulation simulation = new StrategyCandidateSimulation(
                candidate.candidateId(),
                new StrategyCandidateSimulation.Summary(
                        BigDecimal.TEN, new BigDecimal("1000"),
                        new BigDecimal("100"), new BigDecimal("0.1"),
                        5, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, new BigDecimal("100")
                ),
                new StrategyCandidateSimulation.ComparisonToBaseline(
                        BigDecimal.TEN, new BigDecimal("1000"),
                        new BigDecimal("100"), BigDecimal.TEN,
                        BigDecimal.ZERO, new BigDecimal("100")
                ),
                List.of(),
                List.of()
        );
        return new RecommendationCandidateSelection(List.of(
                new StrategyCandidateEvaluationResult.EvaluatedCandidate(
                        candidate, simulation
                )
        ));
    }

    private static StrategyCandidate candidate() {
        BigDecimal quantity = BigDecimal.TEN;
        StrategyCandidate.Location source = new StrategyCandidate.Location(1L, 10L);
        StrategyCandidate.Location target = new StrategyCandidate.Location(1L, 20L);
        StrategyCandidate.Action action = new StrategyCandidate.Action(
                StrategyType.REALLOCATION,
                source,
                target,
                quantity,
                BigDecimal.ZERO,
                List.of(new StrategyCandidate.LotAllocation(
                        1L, 1L, quantity, 1
                ))
        );
        return new StrategyCandidate(
                "CAND-1",
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
    }

    private static BaselineSimulation baseline() {
        return new BaselineSimulation(
                new BaselineSimulation.Summary(
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, null, BigDecimal.TEN, BigDecimal.ZERO
                ),
                List.of()
        );
    }
}
