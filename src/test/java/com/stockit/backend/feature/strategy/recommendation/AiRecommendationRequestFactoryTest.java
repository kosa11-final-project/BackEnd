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
        assertThat(request.schemaVersion()).isEqualTo("ai-strategy-recommendation-v4");
        assertThat(request.minimumRecommendationCount()).isEqualTo(1);
        assertThat(request.maximumRecommendationCount()).isEqualTo(1);
        assertThat(preference.strategyPriority()).isNull();
        assertThat(preference.strategyPrioritySource())
                .isEqualTo(AiRecommendationRequest.PrioritySource.AI_DEFAULT);
        assertThat(preference.targetPriority()).isNull();
        assertThat(preference.targetPrioritySource())
                .isEqualTo(AiRecommendationRequest.PrioritySource.AI_DEFAULT);
        assertThat(request.candidates().get(0).strategyFamilyId()).isNotBlank();
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

    @Test
    void asksAiForThreeToFourRecommendationsBasedOnDistinctFamilies() {
        StrategyCandidate first = candidate("A", 20L, 100);
        StrategyCandidate firstVariant = candidate("A-VARIANT", 20L, 80);
        StrategyCandidate second = candidate("B", 30L, 100);
        StrategyCandidate third = candidate("C", 40L, 100);
        StrategyCandidate fourth = candidate("D", 50L, 100);

        AiRecommendationRequest request = factory.create(
                1L,
                baseline(),
                selection(first, firstVariant, second, third, fourth),
                new StrategyCalculationContext.RequestConstraints(
                        List.of(), List.of(), null, null
                )
        );

        assertThat(request.candidates()).hasSize(5);
        assertThat(request.candidates())
                .extracting(AiRecommendationRequest.CandidateInput::strategyFamilyId)
                .hasSize(5);
        assertThat(request.candidates().stream()
                .map(AiRecommendationRequest.CandidateInput::strategyFamilyId)
                .distinct()).hasSize(4);
        assertThat(request.minimumRecommendationCount()).isEqualTo(3);
        assertThat(request.maximumRecommendationCount()).isEqualTo(4);
    }

    private static RecommendationCandidateSelection selection() {
        return selection(candidate());
    }

    private static RecommendationCandidateSelection selection(
            StrategyCandidate... candidates
    ) {
        return new RecommendationCandidateSelection(java.util.Arrays.stream(candidates)
                .map(candidate -> new StrategyCandidateEvaluationResult.EvaluatedCandidate(
                        candidate,
                        simulation(candidate)
                ))
                .toList());
    }

    private static StrategyCandidateSimulation simulation(StrategyCandidate candidate) {
        return new StrategyCandidateSimulation(
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
    }

    private static StrategyCandidate candidate() {
        return candidate("CAND-1", 20L, 100);
    }

    private static StrategyCandidate candidate(
            String candidateId,
            Long targetSalesPointId,
            int quantityPercentage
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
                        targetSalesPointId, targetSalesPointId, quantity, 1
                ))
        );
        return new StrategyCandidate(
                candidateId,
                List.of(StrategyType.REALLOCATION),
                LocalDate.of(2026, 8, 25),
                null,
                List.of(action),
                List.of(),
                new StrategyCandidate.Preference(1, 1, quantityPercentage),
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
