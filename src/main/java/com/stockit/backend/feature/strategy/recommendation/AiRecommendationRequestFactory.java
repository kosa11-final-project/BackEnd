package com.stockit.backend.feature.strategy.recommendation;

import java.util.List;

import org.springframework.stereotype.Component;

import com.stockit.backend.feature.strategy.calculation.candidate.domain.StrategyCandidate;
import com.stockit.backend.feature.strategy.calculation.domain.BaselineSimulation;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCandidateEvaluationResult;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCandidateSimulation;

/** 서버 계산 결과에서 수치의 출처를 유지한 채 compact LLM 입력을 만든다. */
@Component
public class AiRecommendationRequestFactory {

    private static final String SCHEMA_VERSION = "ai-strategy-recommendation-v1";

    public AiRecommendationRequest create(
            Long strategyCaseId,
            BaselineSimulation baseline,
            RecommendationCandidateSelection selection
    ) {
        int candidateCount = selection.candidates().size();
        int minimum = Math.min(candidateCount, 3);
        int maximum = Math.min(candidateCount, 4);
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
                selection.candidates().stream().map(this::mapCandidate).toList()
        );
    }

    private AiRecommendationRequest.CandidateInput mapCandidate(
            StrategyCandidateEvaluationResult.EvaluatedCandidate evaluated
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
                        candidate.preference().strategyPriority(),
                        candidate.preference().targetPriority(),
                        candidate.preference().quantityPercentage()
                ),
                candidate.evidence().maxExecutableQty()
        );
    }
}
