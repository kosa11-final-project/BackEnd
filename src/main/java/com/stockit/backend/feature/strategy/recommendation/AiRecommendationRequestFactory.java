package com.stockit.backend.feature.strategy.recommendation;

import java.util.List;

import org.springframework.stereotype.Component;

import com.stockit.backend.feature.strategy.calculation.candidate.domain.StrategyCandidate;
import com.stockit.backend.feature.strategy.calculation.domain.BaselineSimulation;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCandidateEvaluationResult;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCandidateSimulation;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;

/** 서버 계산 결과에서 수치의 출처를 유지한 채 compact LLM 입력을 만든다. */
@Component
public class AiRecommendationRequestFactory {

    private static final String SCHEMA_VERSION = "ai-strategy-recommendation-v2";

    public AiRecommendationRequest create(
            Long strategyCaseId,
            BaselineSimulation baseline,
            RecommendationCandidateSelection selection,
            StrategyCalculationContext.RequestConstraints requestConstraints
    ) {
        if (requestConstraints == null) {
            throw new IllegalArgumentException("request constraints must not be null");
        }
        int candidateCount = selection.candidates().size();
        int minimum = candidateCount;
        int maximum = candidateCount;
        BaselineSimulation.Summary summary = baseline.summary();
        return new AiRecommendationRequest(
                SCHEMA_VERSION,
                strategyCaseId,
                minimum,
                maximum,
                new AiRecommendationRequest.BaselineInput(
                        summary.expectedSalesQty(), summary.expectedRevenue(),
                        summary.totalContributionMargin(), summary.contributionMarginRate(),
                        summary.expectedSellThroughDays(), summary.expectedRemainingQty(),
                        summary.expectedDisposalQty()
                ),
                selection.candidates().stream()
                        .map(candidate -> mapCandidate(candidate, requestConstraints))
                        .toList()
        );
    }

    private AiRecommendationRequest.CandidateInput mapCandidate(
            StrategyCandidateEvaluationResult.EvaluatedCandidate evaluated,
            StrategyCalculationContext.RequestConstraints requestConstraints
    ) {
        StrategyCandidate candidate = evaluated.candidate();
        StrategyCandidateSimulation simulation = evaluated.simulation();
        StrategyCandidateSimulation.Summary summary = simulation.summary();
        StrategyCandidateSimulation.ComparisonToBaseline comparison =
                simulation.comparisonToBaseline();
        List<AiRecommendationRequest.ActionInput> actions = candidate.actions().stream()
                .map(action -> new AiRecommendationRequest.ActionInput(
                        action.actionType(), action.source().warehouseId(),
                        action.source().salesPointId(), action.target().warehouseId(),
                        action.target().salesPointId(), action.actionQuantity(),
                        action.estimatedActionCost(), action.strategyPrice(),
                        action.discountRate()
                )).toList();
        return new AiRecommendationRequest.CandidateInput(
                candidate.candidateId(), candidate.strategyTypes(), candidate.startDate(),
                candidate.endDate(), actions,
                new AiRecommendationRequest.SummaryInput(
                        summary.expectedSalesQty(), summary.expectedRevenue(),
                        summary.totalContributionMargin(), summary.contributionMarginRate(),
                        summary.expectedSellThroughDays(), summary.expectedRemainingQty(),
                        summary.expectedDisposalQty(), summary.estimatedActionCost(),
                        summary.netEffect()
                ),
                new AiRecommendationRequest.ComparisonInput(
                        comparison.salesQtyDelta(), comparison.revenueDelta(),
                        comparison.contributionMarginDelta(),
                        comparison.remainingQtyReduction(), comparison.disposalQtyReduction(),
                        comparison.netEffect()
                ),
                candidate.assumptions().stream().map(Enum::name).toList(),
                new AiRecommendationRequest.PreferenceInput(
                        userPriority(
                                requestConstraints.orderedStrategyTypes(),
                                candidate.preference().strategyPriority()
                        ),
                        prioritySource(requestConstraints.orderedStrategyTypes()),
                        userPriority(
                                requestConstraints.orderedCandidateSalesPointIds(),
                                candidate.preference().targetPriority()
                        ),
                        prioritySource(
                                requestConstraints.orderedCandidateSalesPointIds()
                        ),
                        candidate.preference().quantityPercentage()
                ),
                candidate.evidence().maxExecutableQty()
        );
    }

    private static Integer userPriority(List<?> orderedUserValues, int priority) {
        return orderedUserValues.isEmpty() ? null : priority;
    }

    private static AiRecommendationRequest.PrioritySource prioritySource(
            List<?> orderedUserValues
    ) {
        return orderedUserValues.isEmpty()
                ? AiRecommendationRequest.PrioritySource.AI_DEFAULT
                : AiRecommendationRequest.PrioritySource.USER;
    }
}
