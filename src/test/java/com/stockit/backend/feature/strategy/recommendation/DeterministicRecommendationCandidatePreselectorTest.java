package com.stockit.backend.feature.strategy.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.stockit.backend.feature.strategy.calculation.candidate.domain.StrategyCandidate;
import com.stockit.backend.feature.strategy.calculation.domain.BaselineSimulation;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCandidateEvaluationResult;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCandidateSimulation;
import com.stockit.backend.feature.strategy.domain.StrategyType;

class DeterministicRecommendationCandidatePreselectorTest {

    private final DeterministicRecommendationCandidatePreselector preselector =
            new DeterministicRecommendationCandidatePreselector();

    @Test
    void removesOnlyDominatedCandidateWithSameExecutionSignature() {
        var best = evaluated("A", 20L, "10", "100", "2", "0", "1", 1);
        var dominated = evaluated("B", 20L, "9", "90", "3", "1", "2", 1);
        var differentTarget = evaluated("C", 30L, "8", "80", "4", "2", "3", 2);

        RecommendationCandidateSelection result = preselector.select(evaluation(
                List.of(dominated, differentTarget, best)
        ));

        assertThat(result.candidates()).extracting(value -> value.candidate().candidateId())
                .containsExactly("A", "C");
    }

    @Test
    void removesCompositeCandidateWhenAdditionalActionDoesNotChangeOutcome() {
        var reallocation = evaluatedMovement("REALLOCATION", false);
        var concentration = evaluatedMovement("CONCENTRATION", true);

        RecommendationCandidateSelection result = preselector.select(evaluation(
                List.of(concentration, reallocation)
        ));

        assertThat(result.candidates()).extracting(value -> value.candidate().candidateId())
                .containsExactly("REALLOCATION");
    }

    @Test
    void keepsEquivalentOutcomeWhenTargetSalesPointDiffers() {
        var first = evaluated("A", 20L, "10", "100", "0", "0", "0", 1);
        var second = evaluated("B", 30L, "10", "100", "0", "0", "0", 2);

        RecommendationCandidateSelection result = preselector.select(evaluation(
                List.of(first, second)
        ));

        assertThat(result.candidates()).extracting(value -> value.candidate().candidateId())
                .containsExactly("A", "B");
    }

    @Test
    void rejectsEvaluationWithoutSuccessfulCandidate() {
        assertThatThrownBy(() -> preselector.select(evaluation(List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("no evaluated candidate is available");
    }

    @Test
    void capsAtFourWhilePreservingDiversityAndInputOrderIndependence() {
        List<StrategyCandidateEvaluationResult.EvaluatedCandidate> candidates =
                new ArrayList<>();
        for (int index = 0; index < 24; index++) {
            StrategyType type = index % 2 == 0
                    ? StrategyType.REALLOCATION
                    : StrategyType.RT_TRANSFER;
            Long targetSalesPointId = 20L + index % 3;
            candidates.add(evaluatedForDiversity(
                    "CAND-" + index,
                    type,
                    targetSalesPointId,
                    index + 1
            ));
        }

        List<String> selected = preselector.select(evaluation(candidates)).candidates()
                .stream().map(value -> value.candidate().candidateId()).toList();
        Collections.reverse(candidates);
        List<String> selectedFromReversed = preselector.select(evaluation(candidates))
                .candidates().stream()
                .map(value -> value.candidate().candidateId()).toList();

        assertThat(selected).hasSize(
                DeterministicRecommendationCandidatePreselector.MAX_CANDIDATES
        ).containsExactlyElementsOf(selectedFromReversed);
        RecommendationCandidateSelection selection = preselector.select(
                evaluation(candidates)
        );
        assertThat(selection.candidates())
                .extracting(value -> value.candidate().strategyTypes().get(0))
                .contains(StrategyType.REALLOCATION, StrategyType.RT_TRANSFER);
        assertThat(selection.candidates())
                .extracting(value -> value.candidate().actions().get(0)
                        .target().salesPointId())
                .contains(20L, 21L, 22L);
    }

    @Test
    void keepsOnlyBestEconomicRepresentativeWhenQuantityOnlyDiffers() {
        var fullQuantity = evaluatedForDiversity(
                "FULL", StrategyType.REALLOCATION, 20L, 100, "100"
        );
        var partialQuantity = evaluatedForDiversity(
                "PARTIAL", StrategyType.REALLOCATION, 20L, 40, "40"
        );

        RecommendationCandidateSelection result = preselector.select(evaluation(
                List.of(partialQuantity, fullQuantity)
        ));

        assertThat(result.candidates())
                .extracting(value -> value.candidate().candidateId())
                .containsExactly("FULL");
    }

    @Test
    void keepsLeastLossRepresentativeWhenEveryQuantityVariantIsNegative() {
        var fullQuantity = evaluatedForDiversity(
                "FULL", StrategyType.REALLOCATION, 20L, 100, "-100"
        );
        var partialQuantity = evaluatedForDiversity(
                "PARTIAL", StrategyType.REALLOCATION, 20L, 40, "-40"
        );

        RecommendationCandidateSelection result = preselector.select(evaluation(
                List.of(fullQuantity, partialQuantity)
        ));

        assertThat(result.candidates())
                .extracting(value -> value.candidate().candidateId())
                .containsExactly("PARTIAL");
    }

    @Test
    void returnsOneRepresentativePerStrategyAndTargetFamily() {
        var reallocationA = evaluatedForDiversity(
                "REALLOC-A", StrategyType.REALLOCATION, 20L, 100, "100"
        );
        var reallocationB = evaluatedForDiversity(
                "REALLOC-B", StrategyType.REALLOCATION, 30L, 80, "80"
        );
        var transferA = evaluatedForDiversity(
                "TRANSFER-A", StrategyType.RT_TRANSFER, 20L, 60, "60"
        );

        RecommendationCandidateSelection result = preselector.select(evaluation(
                List.of(reallocationA, reallocationB, transferA)
        ));

        assertThat(result.candidates())
                .extracting(value -> value.candidate().candidateId())
                .containsExactly("REALLOC-A", "TRANSFER-A", "REALLOC-B");
    }

    private static StrategyCandidateEvaluationResult evaluation(
            List<StrategyCandidateEvaluationResult.EvaluatedCandidate> candidates
    ) {
        return new StrategyCandidateEvaluationResult(
                mock(com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext.class),
                mock(BaselineSimulation.class), candidates, List.of(), List.of()
        );
    }

    private static StrategyCandidateEvaluationResult.EvaluatedCandidate evaluated(
            String id, Long targetSalesPointId, String sales, String margin,
            String remaining, String disposal, String cost, int targetPriority
    ) {
        StrategyCandidate.Location source = new StrategyCandidate.Location(1L, 10L);
        StrategyCandidate.Location target = new StrategyCandidate.Location(
                1L, targetSalesPointId
        );
        BigDecimal quantity = new BigDecimal("10");
        StrategyCandidate.Action action = new StrategyCandidate.Action(
                StrategyType.PRICE_DISCOUNT, source, target, quantity,
                new BigDecimal(cost), new BigDecimal("900"), new BigDecimal("0.10"),
                List.of(new StrategyCandidate.LotAllocation(1L, 1L, quantity, 1))
        );
        StrategyCandidate candidate = new StrategyCandidate(
                id, List.of(StrategyType.PRICE_DISCOUNT),
                LocalDate.of(2026, 8, 24), LocalDate.of(2026, 8, 31),
                List.of(action), List.of(),
                new StrategyCandidate.Preference(1, targetPriority, 100),
                new StrategyCandidate.DiscountEvidence(
                        quantity, quantity, quantity, quantity,
                        new BigDecimal("1000"), new BigDecimal("700")
                )
        );
        StrategyCandidateSimulation simulation = new StrategyCandidateSimulation(
                id,
                new StrategyCandidateSimulation.Summary(
                        new BigDecimal(sales), BigDecimal.ZERO, new BigDecimal(margin),
                        BigDecimal.ZERO, 5, new BigDecimal(remaining),
                        new BigDecimal(disposal), new BigDecimal(cost), BigDecimal.ZERO
                ),
                new StrategyCandidateSimulation.ComparisonToBaseline(
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
                ), List.of(), List.of()
        );
        return new StrategyCandidateEvaluationResult.EvaluatedCandidate(candidate, simulation);
    }

    private static StrategyCandidateEvaluationResult.EvaluatedCandidate
            evaluatedForDiversity(
            String id,
            StrategyType type,
            Long targetSalesPointId,
            int quantity
    ) {
        return evaluatedForDiversity(
                id, type, targetSalesPointId, quantity, String.valueOf(quantity)
        );
    }

    private static StrategyCandidateEvaluationResult.EvaluatedCandidate
            evaluatedForDiversity(
            String id,
            StrategyType type,
            Long targetSalesPointId,
            int quantity,
            String netEffect
    ) {
        StrategyCandidate.Location source = new StrategyCandidate.Location(1L, 10L);
        StrategyCandidate.Location target = new StrategyCandidate.Location(
                1L, targetSalesPointId
        );
        BigDecimal actionQuantity = BigDecimal.valueOf(quantity);
        StrategyCandidate.Action action = new StrategyCandidate.Action(
                type,
                source,
                target,
                actionQuantity,
                BigDecimal.ZERO,
                List.of(new StrategyCandidate.LotAllocation(
                        (long) quantity,
                        (long) quantity,
                        actionQuantity,
                        1
                ))
        );
        StrategyCandidate candidate = new StrategyCandidate(
                id,
                List.of(type),
                LocalDate.of(2026, 8, 24),
                null,
                List.of(action),
                List.of(),
                new StrategyCandidate.Preference(type == StrategyType.REALLOCATION ? 1 : 2,
                        Math.toIntExact(targetSalesPointId - 19), 100),
                new StrategyCandidate.MovementEvidence(
                        actionQuantity,
                        actionQuantity,
                        actionQuantity,
                        actionQuantity
                )
        );
        StrategyCandidateSimulation simulation = new StrategyCandidateSimulation(
                id,
                new StrategyCandidateSimulation.Summary(
                        BigDecimal.TEN,
                        BigDecimal.ZERO,
                        BigDecimal.TEN,
                        BigDecimal.ZERO,
                        5,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        new BigDecimal(netEffect)
                ),
                new StrategyCandidateSimulation.ComparisonToBaseline(
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        new BigDecimal(netEffect)
                ),
                List.of(),
                List.of()
        );
        return new StrategyCandidateEvaluationResult.EvaluatedCandidate(
                candidate,
                simulation
        );
    }

    private static StrategyCandidateEvaluationResult.EvaluatedCandidate evaluatedMovement(
            String id,
            boolean includeChannelConcentration
    ) {
        StrategyCandidate.Location source = new StrategyCandidate.Location(1L, 1L);
        StrategyCandidate.Location target = new StrategyCandidate.Location(1L, 3L);
        BigDecimal quantity = new BigDecimal("118");
        StrategyCandidate.LotAllocation allocation =
                new StrategyCandidate.LotAllocation(1L, 1L, quantity, 1);
        StrategyCandidate.Action movement = new StrategyCandidate.Action(
                StrategyType.REALLOCATION,
                source,
                target,
                quantity,
                BigDecimal.ZERO,
                List.of(allocation)
        );
        StrategyCandidate candidate;
        if (includeChannelConcentration) {
            StrategyCandidate.Action concentration = new StrategyCandidate.Action(
                    StrategyType.CHANNEL_CONCENTRATION,
                    source,
                    target,
                    quantity,
                    BigDecimal.ZERO,
                    List.of()
            );
            candidate = new StrategyCandidate(
                    id,
                    List.of(StrategyType.CHANNEL_CONCENTRATION, StrategyType.REALLOCATION),
                    LocalDate.of(2026, 8, 24),
                    LocalDate.of(2026, 11, 21),
                    List.of(movement, concentration),
                    List.of(),
                    new StrategyCandidate.Preference(1, 1, 100),
                    new StrategyCandidate.ChannelEvidence(
                            quantity,
                            quantity,
                            BigDecimal.ZERO,
                            quantity,
                            StrategyType.REALLOCATION,
                            new BigDecimal("5500"),
                            BigDecimal.ZERO,
                            BigDecimal.ZERO
                    )
            );
        } else {
            candidate = new StrategyCandidate(
                    id,
                    List.of(StrategyType.REALLOCATION),
                    LocalDate.of(2026, 8, 24),
                    null,
                    List.of(movement),
                    List.of(),
                    new StrategyCandidate.Preference(1, 1, 100),
                    new StrategyCandidate.MovementEvidence(
                            quantity,
                            quantity,
                            quantity,
                            quantity
                    )
            );
        }

        StrategyCandidateSimulation simulation = new StrategyCandidateSimulation(
                id,
                new StrategyCandidateSimulation.Summary(
                        quantity,
                        new BigDecimal("649000"),
                        new BigDecimal("212420"),
                        new BigDecimal("0.3273"),
                        30,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        new BigDecimal("228920")
                ),
                new StrategyCandidateSimulation.ComparisonToBaseline(
                        BigDecimal.ZERO,
                        new BigDecimal("7080"),
                        new BigDecimal("228920"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        new BigDecimal("228920")
                ),
                List.of(),
                List.of()
        );
        return new StrategyCandidateEvaluationResult.EvaluatedCandidate(candidate, simulation);
    }
}
