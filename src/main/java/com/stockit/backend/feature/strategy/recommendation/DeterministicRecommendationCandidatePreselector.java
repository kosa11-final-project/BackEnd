package com.stockit.backend.feature.strategy.recommendation;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.stereotype.Component;

import com.stockit.backend.feature.strategy.calculation.candidate.domain.CandidateAssumption;
import com.stockit.backend.feature.strategy.calculation.candidate.domain.StrategyCandidate;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCandidateEvaluationResult;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCandidateSimulation;
import com.stockit.backend.feature.strategy.domain.StrategyType;

/**
 * 사용자 우선순위와 전략·판매처 다양성을 보존하면서 LLM 입력 후보를 최종 노출 개수로 축약한다.
 * 수량·할인율·기간만 다른 동일 실행 구조는 한 전략군으로 묶고 대표 후보 하나만 유지한다.
 */
@Component
public class DeterministicRecommendationCandidatePreselector
        implements RecommendationCandidatePreselector {

    static final int MAX_CANDIDATES = 4;

    private static final Comparator<StrategyCandidateEvaluationResult.EvaluatedCandidate>
            METRIC_ORDER = Comparator
            .comparing((StrategyCandidateEvaluationResult.EvaluatedCandidate value) ->
                            value.simulation().comparisonToBaseline().netEffect(),
                    Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(value -> value.simulation().summary().expectedRemainingQty(),
                    DeterministicRecommendationCandidatePreselector::compareNullableDecimal)
            .thenComparing(value -> value.simulation().summary().expectedDisposalQty(),
                    DeterministicRecommendationCandidatePreselector::compareNullableDecimal)
            .thenComparing(value -> value.simulation().summary().totalContributionMargin(),
                    Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(value -> value.simulation().summary().expectedSalesQty(),
                    Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(value -> value.simulation().summary().expectedSellThroughDays(),
                    Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(value -> value.simulation().summary().estimatedActionCost(),
                    DeterministicRecommendationCandidatePreselector::compareNullableDecimal)
            .thenComparing(value -> value.candidate().candidateId());

    @Override
    public RecommendationCandidateSelection select(
            StrategyCandidateEvaluationResult evaluation
    ) {
        if (evaluation == null) {
            throw new IllegalArgumentException("candidate evaluation must not be null");
        }

        List<StrategyCandidateEvaluationResult.EvaluatedCandidate> unique =
                deduplicateByCandidateId(evaluation.evaluatedCandidates());
        if (unique.isEmpty()) {
            throw new IllegalArgumentException("no evaluated candidate is available");
        }

        Comparator<StrategyCandidateEvaluationResult.EvaluatedCandidate> candidateOrder =
                candidateOrder(evaluation);
        List<StrategyCandidateEvaluationResult.EvaluatedCandidate> nonRedundant =
                removeRedundantEquivalentCandidates(unique, candidateOrder);
        List<StrategyCandidateEvaluationResult.EvaluatedCandidate> pareto =
                removeDominatedCandidates(nonRedundant, candidateOrder);
        List<StrategyCandidateEvaluationResult.EvaluatedCandidate> representatives =
                selectFamilyRepresentatives(pareto, candidateOrder);
        List<StrategyCandidateEvaluationResult.EvaluatedCandidate> selected =
                takeDiverseCandidates(representatives, MAX_CANDIDATES, candidateOrder);
        return new RecommendationCandidateSelection(selected);
    }

    private static Comparator<StrategyCandidateEvaluationResult.EvaluatedCandidate>
            candidateOrder(StrategyCandidateEvaluationResult evaluation) {
        boolean userStrategyPriority = evaluation.calculationContext() != null
                && evaluation.calculationContext().requestConstraints() != null
                && !evaluation.calculationContext().requestConstraints()
                .orderedStrategyTypes().isEmpty();
        boolean userTargetPriority = evaluation.calculationContext() != null
                && evaluation.calculationContext().requestConstraints() != null
                && !evaluation.calculationContext().requestConstraints()
                .orderedCandidateSalesPointIds().isEmpty();
        return Comparator
                .comparingInt((StrategyCandidateEvaluationResult.EvaluatedCandidate value) ->
                        userStrategyPriority
                                ? value.candidate().preference().strategyPriority()
                                : 0)
                .thenComparingInt(value -> userTargetPriority
                        ? value.candidate().preference().targetPriority()
                        : 0)
                .thenComparing(METRIC_ORDER);
    }

    private static List<StrategyCandidateEvaluationResult.EvaluatedCandidate>
            deduplicateByCandidateId(
            List<StrategyCandidateEvaluationResult.EvaluatedCandidate> candidates
    ) {
        Map<String, StrategyCandidateEvaluationResult.EvaluatedCandidate> byId =
                new TreeMap<>();
        for (StrategyCandidateEvaluationResult.EvaluatedCandidate candidate : candidates) {
            byId.putIfAbsent(candidate.candidate().candidateId(), candidate);
        }
        return List.copyOf(byId.values());
    }

    /**
     * 정량 결과가 완전히 같고 한 후보의 실행 액션이 다른 후보에 모두 포함되면
     * 추가 액션은 실제 효과가 없는 것으로 보고 더 단순한 후보만 유지한다.
     */
    private static List<StrategyCandidateEvaluationResult.EvaluatedCandidate>
            removeRedundantEquivalentCandidates(
            List<StrategyCandidateEvaluationResult.EvaluatedCandidate> candidates,
            Comparator<StrategyCandidateEvaluationResult.EvaluatedCandidate> candidateOrder
    ) {
        List<StrategyCandidateEvaluationResult.EvaluatedCandidate> retained =
                new ArrayList<>();
        candidates.stream().sorted(Comparator
                .comparingInt((StrategyCandidateEvaluationResult.EvaluatedCandidate value) ->
                        value.candidate().actions().size())
                .thenComparing(candidateOrder)).forEach(candidate -> {
            boolean redundant = retained.stream().anyMatch(existing ->
                    sameSimulationOutcome(existing.simulation(), candidate.simulation())
                            && executionContainedIn(
                            existing.candidate(), candidate.candidate()));
            if (!redundant) {
                retained.add(candidate);
            }
        });
        return List.copyOf(retained);
    }

    private static boolean sameSimulationOutcome(
            StrategyCandidateSimulation left,
            StrategyCandidateSimulation right
    ) {
        return simulationOutcomeSignature(left).equals(simulationOutcomeSignature(right));
    }

    private static boolean executionContainedIn(
            StrategyCandidate simpler,
            StrategyCandidate complex
    ) {
        if (simpler.actions().size() > complex.actions().size()) {
            return false;
        }
        List<ExecutionActionSignature> remaining = new ArrayList<>(
                complex.actions().stream()
                        .map(DeterministicRecommendationCandidatePreselector::executionSignature)
                        .toList()
        );
        for (StrategyCandidate.Action action : simpler.actions()) {
            if (!remaining.remove(executionSignature(action))) {
                return false;
            }
        }
        return true;
    }

    private static ExecutionActionSignature executionSignature(
            StrategyCandidate.Action action
    ) {
        List<LotAllocationSignature> allocations = action.lotAllocations().stream()
                .map(allocation -> new LotAllocationSignature(
                        allocation.inventoryBalanceId(),
                        allocation.lotId(),
                        normalizeDecimal(allocation.quantity()),
                        allocation.priorityNo()
                ))
                .sorted(Comparator
                        .comparingInt(LotAllocationSignature::priorityNo)
                        .thenComparing(LotAllocationSignature::inventoryBalanceId)
                        .thenComparing(LotAllocationSignature::lotId))
                .toList();
        return new ExecutionActionSignature(
                action.actionType(),
                action.source().warehouseId(),
                action.source().salesPointId(),
                action.target().warehouseId(),
                action.target().salesPointId(),
                normalizeDecimal(action.actionQuantity()),
                normalizeDecimal(action.estimatedActionCost()),
                normalizeDecimal(action.strategyPrice()),
                normalizeDecimal(action.discountRate()),
                allocations
        );
    }

    private static SimulationOutcomeSignature simulationOutcomeSignature(
            StrategyCandidateSimulation simulation
    ) {
        StrategyCandidateSimulation.Summary summary = simulation.summary();
        StrategyCandidateSimulation.ComparisonToBaseline comparison =
                simulation.comparisonToBaseline();
        List<DailyOutcomeSignature> dailySeries = simulation.dailySeries().stream()
                .map(point -> new DailyOutcomeSignature(
                        point.date(),
                        normalizeDecimal(point.expectedSalesQty()),
                        normalizeDecimal(point.expectedRemainingQty()),
                        normalizeDecimal(point.cumulativeRevenue()),
                        normalizeDecimal(point.cumulativeContributionMargin())
                ))
                .toList();
        return new SimulationOutcomeSignature(
                normalizeDecimal(summary.expectedSalesQty()),
                normalizeDecimal(summary.expectedRevenue()),
                normalizeDecimal(summary.totalContributionMargin()),
                normalizeDecimal(summary.contributionMarginRate()),
                summary.expectedSellThroughDays(),
                normalizeDecimal(summary.expectedRemainingQty()),
                normalizeDecimal(summary.expectedDisposalQty()),
                normalizeDecimal(summary.estimatedActionCost()),
                normalizeDecimal(summary.netEffect()),
                normalizeDecimal(comparison.salesQtyDelta()),
                normalizeDecimal(comparison.revenueDelta()),
                normalizeDecimal(comparison.contributionMarginDelta()),
                normalizeDecimal(comparison.remainingQtyReduction()),
                normalizeDecimal(comparison.disposalQtyReduction()),
                normalizeDecimal(comparison.netEffect()),
                dailySeries
        );
    }

    private static BigDecimal normalizeDecimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros();
    }

    private static List<StrategyCandidateEvaluationResult.EvaluatedCandidate>
            removeDominatedCandidates(
            List<StrategyCandidateEvaluationResult.EvaluatedCandidate> candidates,
            Comparator<StrategyCandidateEvaluationResult.EvaluatedCandidate> candidateOrder
    ) {
        Map<CandidateSignature, List<StrategyCandidateEvaluationResult.EvaluatedCandidate>>
                comparableGroups = new LinkedHashMap<>();
        candidates.stream()
                .sorted(candidateOrder)
                .forEach(candidate -> comparableGroups
                        .computeIfAbsent(signature(candidate.candidate()), ignored ->
                                new ArrayList<>())
                        .add(candidate));

        List<StrategyCandidateEvaluationResult.EvaluatedCandidate> result =
                new ArrayList<>();
        for (List<StrategyCandidateEvaluationResult.EvaluatedCandidate> group
                : comparableGroups.values()) {
            for (StrategyCandidateEvaluationResult.EvaluatedCandidate candidate : group) {
                boolean dominated = group.stream()
                        .filter(other -> other != candidate)
                        .anyMatch(other -> dominates(other, candidate));
                if (!dominated) {
                    result.add(candidate);
                }
            }
        }
        return result;
    }

    /**
     * 시뮬레이션 화면에서 다시 조정할 수 있는 수량·할인율·기간 차이는 전략 대안으로
     * 중복 노출하지 않는다. 전략 타입과 출발·도착 실행 경로가 같은 후보 중 정량 결과가
     * 가장 나은 대표 후보 하나만 LLM에 전달한다.
     */
    private static List<StrategyCandidateEvaluationResult.EvaluatedCandidate>
            selectFamilyRepresentatives(
            List<StrategyCandidateEvaluationResult.EvaluatedCandidate> candidates,
            Comparator<StrategyCandidateEvaluationResult.EvaluatedCandidate> candidateOrder
    ) {
        Map<RecommendationFamilyKey,
                StrategyCandidateEvaluationResult.EvaluatedCandidate> representatives =
                new LinkedHashMap<>();
        candidates.stream().sorted(candidateOrder).forEach(candidate ->
                representatives.putIfAbsent(
                        familyKey(candidate.candidate()), candidate
                ));
        return List.copyOf(representatives.values());
    }

    private static boolean dominates(
            StrategyCandidateEvaluationResult.EvaluatedCandidate left,
            StrategyCandidateEvaluationResult.EvaluatedCandidate right
    ) {
        Set<CandidateAssumption> leftAssumptions = new LinkedHashSet<>(
                left.candidate().assumptions()
        );
        Set<CandidateAssumption> rightAssumptions = new LinkedHashSet<>(
                right.candidate().assumptions()
        );
        if (!rightAssumptions.containsAll(leftAssumptions)) {
            return false;
        }

        StrategyCandidateSimulation.Summary a = left.simulation().summary();
        StrategyCandidateSimulation.Summary b = right.simulation().summary();
        boolean noWorse = greaterOrEqual(a.expectedSalesQty(), b.expectedSalesQty())
                && greaterOrEqual(
                a.totalContributionMargin(), b.totalContributionMargin())
                && lessOrEqual(a.expectedRemainingQty(), b.expectedRemainingQty())
                && lessOrEqual(a.expectedDisposalQty(), b.expectedDisposalQty())
                && lessOrEqual(a.estimatedActionCost(), b.estimatedActionCost())
                && sellThroughNoWorse(
                a.expectedSellThroughDays(), b.expectedSellThroughDays());
        if (!noWorse) {
            return false;
        }
        return greater(a.expectedSalesQty(), b.expectedSalesQty())
                || greater(a.totalContributionMargin(), b.totalContributionMargin())
                || less(a.expectedRemainingQty(), b.expectedRemainingQty())
                || less(a.expectedDisposalQty(), b.expectedDisposalQty())
                || less(a.estimatedActionCost(), b.estimatedActionCost())
                || sellThroughBetter(
                a.expectedSellThroughDays(), b.expectedSellThroughDays())
                || leftAssumptions.size() < rightAssumptions.size();
    }

    private static List<StrategyCandidateEvaluationResult.EvaluatedCandidate>
            takeDiverseCandidates(
            List<StrategyCandidateEvaluationResult.EvaluatedCandidate> candidates,
            int limit,
            Comparator<StrategyCandidateEvaluationResult.EvaluatedCandidate> candidateOrder
    ) {
        Map<StrategyType, Map<String,
                Deque<StrategyCandidateEvaluationResult.EvaluatedCandidate>>> buckets =
                new LinkedHashMap<>();

        candidates.stream().sorted(candidateOrder).forEach(candidate -> {
            StrategyType type = candidate.candidate().strategyTypes().get(0);
            String target = targetKey(candidate.candidate());
            buckets.computeIfAbsent(type, ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(target, ignored -> new ArrayDeque<>())
                    .add(candidate);
        });

        Map<StrategyType, Deque<String>> targetCycles = new LinkedHashMap<>();
        buckets.forEach((type, targets) ->
                targetCycles.put(type, new ArrayDeque<>(targets.keySet())));

        List<StrategyCandidateEvaluationResult.EvaluatedCandidate> selected =
                new ArrayList<>();
        boolean added;
        do {
            added = false;
            for (Map.Entry<StrategyType, Map<String,
                    Deque<StrategyCandidateEvaluationResult.EvaluatedCandidate>>> entry
                    : buckets.entrySet()) {
                if (selected.size() >= limit) {
                    break;
                }
                Deque<String> cycle = targetCycles.get(entry.getKey());
                StrategyCandidateEvaluationResult.EvaluatedCandidate next =
                        pollNext(entry.getValue(), cycle);
                if (next != null) {
                    selected.add(next);
                    added = true;
                }
            }
        } while (added && selected.size() < limit);
        return selected;
    }

    private static StrategyCandidateEvaluationResult.EvaluatedCandidate pollNext(
            Map<String, Deque<StrategyCandidateEvaluationResult.EvaluatedCandidate>> buckets,
            Deque<String> cycle
    ) {
        int remainingTargets = cycle.size();
        while (remainingTargets-- > 0) {
            String key = cycle.removeFirst();
            Deque<StrategyCandidateEvaluationResult.EvaluatedCandidate> queue =
                    buckets.get(key);
            StrategyCandidateEvaluationResult.EvaluatedCandidate next = queue.pollFirst();
            if (!queue.isEmpty()) {
                cycle.addLast(key);
            }
            if (next != null) {
                return next;
            }
        }
        return null;
    }

    private static CandidateSignature signature(StrategyCandidate candidate) {
        List<ActionSignature> actions = candidate.actions().stream()
                .map(action -> new ActionSignature(
                        action.actionType(),
                        action.source().warehouseId(),
                        action.source().salesPointId(),
                        action.target().warehouseId(),
                        action.target().salesPointId()
                ))
                .toList();
        return new CandidateSignature(
                candidate.strategyTypes(),
                candidate.startDate().toString(),
                candidate.endDate() == null ? null : candidate.endDate().toString(),
                actions
        );
    }

    private static String targetKey(StrategyCandidate candidate) {
        return candidate.actions().stream()
                .map(StrategyCandidate.Action::target)
                .map(location -> location.salesPointId() != null
                        ? "S:" + location.salesPointId()
                        : "W:" + location.warehouseId())
                .sorted()
                .findFirst()
                .orElse("NONE");
    }

    private static RecommendationFamilyKey familyKey(StrategyCandidate candidate) {
        return new RecommendationFamilyKey(
                candidate.strategyTypes(),
                candidate.actions().stream()
                        .map(action -> new ActionSignature(
                                action.actionType(),
                                action.source().warehouseId(),
                                action.source().salesPointId(),
                                action.target().warehouseId(),
                                action.target().salesPointId()
                        ))
                        .toList()
        );
    }

    private static int compareNullableDecimal(BigDecimal left, BigDecimal right) {
        return Comparator.nullsLast(BigDecimal::compareTo).compare(left, right);
    }

    private static boolean greaterOrEqual(BigDecimal left, BigDecimal right) {
        return bothMissing(left, right)
                || (left != null && right != null && left.compareTo(right) >= 0);
    }

    private static boolean lessOrEqual(BigDecimal left, BigDecimal right) {
        return bothMissing(left, right)
                || (left != null && right != null && left.compareTo(right) <= 0);
    }

    private static boolean greater(BigDecimal left, BigDecimal right) {
        return left != null && right != null && left.compareTo(right) > 0;
    }

    private static boolean less(BigDecimal left, BigDecimal right) {
        return left != null && right != null && left.compareTo(right) < 0;
    }

    private static boolean bothMissing(BigDecimal left, BigDecimal right) {
        return left == null && right == null;
    }

    private static boolean sellThroughNoWorse(Integer left, Integer right) {
        if (right == null) {
            return true;
        }
        return left != null && left <= right;
    }

    private static boolean sellThroughBetter(Integer left, Integer right) {
        return left != null && (right == null || left < right);
    }

    private record CandidateSignature(
            List<StrategyType> types,
            String startDate,
            String endDate,
            List<ActionSignature> actions
    ) {
        private CandidateSignature {
            types = List.copyOf(types);
            actions = List.copyOf(actions);
        }
    }

    private record RecommendationFamilyKey(
            List<StrategyType> types,
            List<ActionSignature> actions
    ) {
        private RecommendationFamilyKey {
            types = List.copyOf(types);
            actions = List.copyOf(actions);
        }
    }

    private record ActionSignature(
            StrategyType type,
            Long sourceWarehouseId,
            Long sourceSalesPointId,
            Long targetWarehouseId,
            Long targetSalesPointId
    ) {
    }

    private record ExecutionActionSignature(
            StrategyType type,
            Long sourceWarehouseId,
            Long sourceSalesPointId,
            Long targetWarehouseId,
            Long targetSalesPointId,
            BigDecimal actionQuantity,
            BigDecimal estimatedActionCost,
            BigDecimal strategyPrice,
            BigDecimal discountRate,
            List<LotAllocationSignature> lotAllocations
    ) {
        private ExecutionActionSignature {
            lotAllocations = List.copyOf(lotAllocations);
        }
    }

    private record LotAllocationSignature(
            Long inventoryBalanceId,
            Long lotId,
            BigDecimal quantity,
            int priorityNo
    ) {
    }

    private record SimulationOutcomeSignature(
            BigDecimal expectedSalesQty,
            BigDecimal expectedRevenue,
            BigDecimal totalContributionMargin,
            BigDecimal contributionMarginRate,
            Integer expectedSellThroughDays,
            BigDecimal expectedRemainingQty,
            BigDecimal expectedDisposalQty,
            BigDecimal estimatedActionCost,
            BigDecimal netEffect,
            BigDecimal salesQtyDelta,
            BigDecimal revenueDelta,
            BigDecimal contributionMarginDelta,
            BigDecimal remainingQtyReduction,
            BigDecimal disposalQtyReduction,
            BigDecimal baselineNetEffect,
            List<DailyOutcomeSignature> dailySeries
    ) {
        private SimulationOutcomeSignature {
            dailySeries = List.copyOf(dailySeries);
        }
    }

    private record DailyOutcomeSignature(
            java.time.LocalDate date,
            BigDecimal expectedSalesQty,
            BigDecimal expectedRemainingQty,
            BigDecimal cumulativeRevenue,
            BigDecimal cumulativeContributionMargin
    ) {
    }
}
