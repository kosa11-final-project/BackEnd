package com.stockit.backend.feature.strategy.recommendation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.stockit.backend.feature.strategy.domain.StrategyType;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;

/** 서버가 제공한 동일 후보 집합을 기준으로 LLM 선택 품질을 결정론적으로 평가한다. */
@Component
public class AiRecommendationQualityEvaluator {

    private static final int RATIO_SCALE = 6;

    public AiRecommendationQualityEvaluation evaluate(
            AiRecommendationRequest request,
            AiRecommendationProviderResponse response
    ) {
        return evaluate(request, response, null);
    }

    public AiRecommendationQualityEvaluation evaluate(
            AiRecommendationRequest request,
            AiRecommendationProviderResponse response,
            StrategyCalculationContext.RequestConstraints constraints
    ) {
        if (request == null || response == null) {
            throw new IllegalArgumentException("recommendation quality input is required");
        }

        Map<String, AiRecommendationRequest.CandidateInput> allowed =
                new LinkedHashMap<>();
        request.candidates().forEach(candidate ->
                allowed.putIfAbsent(candidate.candidateId(), candidate));
        List<AiRecommendationProviderResponse.Recommendation> recommendations =
                response.recommendations() == null
                        ? List.of() : response.recommendations();

        StructuralSelection selection = inspectSelection(
                allowed, recommendations
        );
        AiRecommendationRequest.CandidateInput top1 = selection.validSelections().stream()
                .min(Comparator.comparingInt(ValidSelection::rank))
                .map(ValidSelection::candidate)
                .orElse(null);

        BigDecimal bestNetEffect = request.candidates().stream()
                .map(AiRecommendationQualityEvaluator::netEffect)
                .filter(Objects::nonNull)
                .max(BigDecimal::compareTo)
                .orElse(null);
        BigDecimal top1NetEffect = top1 == null ? null : netEffect(top1);
        BigDecimal regret = regret(bestNetEffect, top1NetEffect);
        BigDecimal regretRate = regretRate(bestNetEffect, regret);

        Set<String> families = new HashSet<>();
        Set<StrategyType> strategyTypes = new HashSet<>();
        Set<Long> targetSalesPoints = new HashSet<>();
        for (ValidSelection selected : selection.validSelections()) {
            AiRecommendationRequest.CandidateInput candidate = selected.candidate();
            families.add(candidate.strategyFamilyId());
            strategyTypes.addAll(candidate.strategyTypes());
            candidate.actions().stream()
                    .map(AiRecommendationRequest.ActionInput::targetSalesPointId)
                    .filter(Objects::nonNull)
                    .forEach(targetSalesPoints::add);
        }

        return new AiRecommendationQualityEvaluation(
                request.candidates().size(),
                recommendations.size(),
                selection.validSelections().size(),
                selection.structuralViolationCount(),
                families.size(),
                strategyTypes.size(),
                targetSalesPoints.size(),
                bestNetEffect,
                top1NetEffect,
                regret,
                regretRate,
                priorityCompliant(
                        request.candidates(), top1,
                        AiRecommendationRequest.PreferenceInput::strategyPriority,
                        AiRecommendationRequest.PreferenceInput::strategyPrioritySource
                ),
                priorityCompliant(
                        request.candidates(), top1,
                        AiRecommendationRequest.PreferenceInput::targetPriority,
                        AiRecommendationRequest.PreferenceInput::targetPrioritySource
                ),
                constraints == null ? null : fixedConstraintViolations(
                        selection.validSelections(), constraints
                )
        );
    }

    private static int fixedConstraintViolations(
            List<ValidSelection> selections,
            StrategyCalculationContext.RequestConstraints constraints
    ) {
        int violations = 0;
        for (ValidSelection selection : selections) {
            AiRecommendationRequest.CandidateInput candidate = selection.candidate();
            if (constraints.preferredStartDate() != null
                    && !constraints.preferredStartDate().equals(candidate.startDate())) {
                violations++;
            }
            if (constraints.preferredEndDate() != null
                    && !constraints.preferredEndDate().equals(candidate.endDate())) {
                violations++;
            }
            if (!constraints.orderedStrategyTypes().isEmpty()
                    && candidate.strategyTypes().stream().anyMatch(type ->
                    !constraints.orderedStrategyTypes().contains(type))) {
                violations++;
            }
            if (!constraints.orderedCandidateSalesPointIds().isEmpty()
                    && candidate.actions().stream()
                    .map(AiRecommendationRequest.ActionInput::targetSalesPointId)
                    .filter(Objects::nonNull)
                    .anyMatch(salesPointId -> !constraints
                            .orderedCandidateSalesPointIds().contains(salesPointId))) {
                violations++;
            }
        }
        return violations;
    }

    private static StructuralSelection inspectSelection(
            Map<String, AiRecommendationRequest.CandidateInput> allowed,
            List<AiRecommendationProviderResponse.Recommendation> recommendations
    ) {
        int violations = 0;
        Set<String> candidateIds = new HashSet<>();
        Set<String> familyIds = new HashSet<>();
        Set<Integer> ranks = new HashSet<>();
        List<ValidSelection> valid = new ArrayList<>();

        for (AiRecommendationProviderResponse.Recommendation recommendation
                : recommendations) {
            if (recommendation == null) {
                violations++;
                continue;
            }
            AiRecommendationRequest.CandidateInput candidate = allowed.get(
                    recommendation.candidateId()
            );
            boolean candidateValid = candidate != null
                    && candidateIds.add(recommendation.candidateId());
            boolean rankValid = recommendation.rank() >= 1
                    && recommendation.rank() <= recommendations.size()
                    && ranks.add(recommendation.rank());
            boolean familyValid = candidate != null
                    && familyIds.add(candidate.strategyFamilyId());
            if (!candidateValid || !rankValid || !familyValid) {
                violations++;
                continue;
            }
            valid.add(new ValidSelection(recommendation.rank(), candidate));
        }
        for (int rank = 1; rank <= recommendations.size(); rank++) {
            if (!ranks.contains(rank)) {
                violations++;
            }
        }
        return new StructuralSelection(List.copyOf(valid), violations);
    }

    private static BigDecimal netEffect(
            AiRecommendationRequest.CandidateInput candidate
    ) {
        return candidate.comparisonToBaseline() == null
                ? null : candidate.comparisonToBaseline().netEffect();
    }

    private static BigDecimal regret(
            BigDecimal bestNetEffect,
            BigDecimal top1NetEffect
    ) {
        if (bestNetEffect == null || top1NetEffect == null) {
            return null;
        }
        return bestNetEffect.subtract(top1NetEffect).max(BigDecimal.ZERO);
    }

    private static BigDecimal regretRate(
            BigDecimal bestNetEffect,
            BigDecimal regret
    ) {
        if (bestNetEffect == null || regret == null) {
            return null;
        }
        BigDecimal denominator = bestNetEffect.abs().max(BigDecimal.ONE);
        return regret.divide(denominator, RATIO_SCALE, RoundingMode.HALF_UP);
    }

    private static Boolean priorityCompliant(
            List<AiRecommendationRequest.CandidateInput> candidates,
            AiRecommendationRequest.CandidateInput top1,
            java.util.function.Function<AiRecommendationRequest.PreferenceInput, Integer>
                    priorityExtractor,
            java.util.function.Function<AiRecommendationRequest.PreferenceInput,
                    AiRecommendationRequest.PrioritySource> sourceExtractor
    ) {
        if (top1 == null || top1.preference() == null
                || sourceExtractor.apply(top1.preference())
                != AiRecommendationRequest.PrioritySource.USER) {
            return null;
        }
        Integer top1Priority = priorityExtractor.apply(top1.preference());
        Integer bestPriority = candidates.stream()
                .map(AiRecommendationRequest.CandidateInput::preference)
                .filter(Objects::nonNull)
                .filter(preference -> sourceExtractor.apply(preference)
                        == AiRecommendationRequest.PrioritySource.USER)
                .map(priorityExtractor)
                .filter(Objects::nonNull)
                .min(Integer::compareTo)
                .orElse(null);
        return bestPriority != null && Objects.equals(top1Priority, bestPriority);
    }

    private record ValidSelection(
            int rank,
            AiRecommendationRequest.CandidateInput candidate
    ) {
    }

    private record StructuralSelection(
            List<ValidSelection> validSelections,
            int structuralViolationCount
    ) {
    }
}
