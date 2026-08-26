package com.stockit.backend.feature.strategy.recommendation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.stockit.backend.feature.strategy.domain.StrategyType;

/** 일별 시계열과 LOT 상세를 제외한 LLM 입력 계약. */
public record AiRecommendationRequest(
        String schemaVersion,
        Long strategyCaseId,
        int minimumRecommendationCount,
        int maximumRecommendationCount,
        BaselineInput baseline,
        List<CandidateInput> candidates
) {
    public AiRecommendationRequest {
        if (schemaVersion == null || schemaVersion.isBlank()
                || strategyCaseId == null || strategyCaseId <= 0
                || minimumRecommendationCount <= 0
                || maximumRecommendationCount < minimumRecommendationCount
                || baseline == null || candidates == null || candidates.isEmpty()
                || maximumRecommendationCount > candidates.size()) {
            throw new IllegalArgumentException("AI recommendation request is invalid");
        }
        candidates = List.copyOf(candidates);
    }

    public record BaselineInput(
            BigDecimal expectedSalesQty,
            BigDecimal expectedRevenue,
            BigDecimal totalContributionMargin,
            BigDecimal contributionMarginRate,
            Integer expectedSellThroughDays,
            BigDecimal expectedRemainingQty,
            BigDecimal expectedDisposalQty,
            BigDecimal expectedDisposalCost,
            BigDecimal expectedHoldingCost
    ) {
        public BaselineInput(
                BigDecimal expectedSalesQty,
                BigDecimal expectedRevenue,
                BigDecimal totalContributionMargin,
                BigDecimal contributionMarginRate,
                Integer expectedSellThroughDays,
                BigDecimal expectedRemainingQty,
                BigDecimal expectedDisposalQty
        ) {
            this(
                    expectedSalesQty, expectedRevenue, totalContributionMargin,
                    contributionMarginRate, expectedSellThroughDays,
                    expectedRemainingQty, expectedDisposalQty,
                    BigDecimal.ZERO, BigDecimal.ZERO
            );
        }
    }

    public record CandidateInput(
            String candidateId,
            String strategyFamilyId,
            List<StrategyType> strategyTypes,
            LocalDate startDate,
            LocalDate endDate,
            List<ActionInput> actions,
            SummaryInput summary,
            ComparisonInput comparisonToBaseline,
            List<String> assumptions,
            PreferenceInput preference,
            BigDecimal maxExecutableQty
    ) {
        public CandidateInput {
            if (candidateId == null || candidateId.isBlank()
                    || strategyFamilyId == null || strategyFamilyId.isBlank()) {
                throw new IllegalArgumentException("AI recommendation candidate is invalid");
            }
            strategyTypes = List.copyOf(strategyTypes);
            actions = List.copyOf(actions);
            assumptions = List.copyOf(assumptions);
        }
    }

    public record ActionInput(
            StrategyType actionType,
            Long sourceWarehouseId,
            Long sourceSalesPointId,
            Long targetWarehouseId,
            Long targetSalesPointId,
            BigDecimal actionQuantity,
            BigDecimal estimatedActionCost,
            BigDecimal strategyPrice,
            BigDecimal discountRate
    ) {
    }

    public record SummaryInput(
            BigDecimal expectedSalesQty,
            BigDecimal expectedRevenue,
            BigDecimal totalContributionMargin,
            BigDecimal contributionMarginRate,
            Integer expectedSellThroughDays,
            BigDecimal expectedRemainingQty,
            BigDecimal expectedDisposalQty,
            BigDecimal expectedDisposalCost,
            BigDecimal expectedHoldingCost,
            BigDecimal estimatedActionCost,
            BigDecimal netEffect
    ) {
        public SummaryInput(
                BigDecimal expectedSalesQty,
                BigDecimal expectedRevenue,
                BigDecimal totalContributionMargin,
                BigDecimal contributionMarginRate,
                Integer expectedSellThroughDays,
                BigDecimal expectedRemainingQty,
                BigDecimal expectedDisposalQty,
                BigDecimal estimatedActionCost,
                BigDecimal netEffect
        ) {
            this(
                    expectedSalesQty, expectedRevenue, totalContributionMargin,
                    contributionMarginRate, expectedSellThroughDays,
                    expectedRemainingQty, expectedDisposalQty,
                    BigDecimal.ZERO, BigDecimal.ZERO,
                    estimatedActionCost, netEffect
            );
        }
    }

    public record ComparisonInput(
            BigDecimal salesQtyDelta,
            BigDecimal revenueDelta,
            BigDecimal contributionMarginDelta,
            BigDecimal remainingQtyReduction,
            BigDecimal disposalQtyReduction,
            BigDecimal avoidedDisposalCost,
            BigDecimal avoidedHoldingCost,
            BigDecimal netEffect
    ) {
        public ComparisonInput(
                BigDecimal salesQtyDelta,
                BigDecimal revenueDelta,
                BigDecimal contributionMarginDelta,
                BigDecimal remainingQtyReduction,
                BigDecimal disposalQtyReduction,
                BigDecimal netEffect
        ) {
            this(
                    salesQtyDelta, revenueDelta, contributionMarginDelta,
                    remainingQtyReduction, disposalQtyReduction,
                    BigDecimal.ZERO, BigDecimal.ZERO, netEffect
            );
        }
    }

    public record PreferenceInput(
            Integer strategyPriority,
            PrioritySource strategyPrioritySource,
            Integer targetPriority,
            PrioritySource targetPrioritySource,
            int quantityPercentage
    ) {
        public PreferenceInput {
            if (strategyPrioritySource == null || targetPrioritySource == null
                    || (strategyPrioritySource == PrioritySource.USER
                    && (strategyPriority == null || strategyPriority <= 0))
                    || (strategyPrioritySource == PrioritySource.AI_DEFAULT
                    && strategyPriority != null)
                    || (targetPrioritySource == PrioritySource.USER
                    && (targetPriority == null || targetPriority <= 0))
                    || (targetPrioritySource == PrioritySource.AI_DEFAULT
                    && targetPriority != null)
                    || quantityPercentage <= 0 || quantityPercentage > 100) {
                throw new IllegalArgumentException("candidate preference input is invalid");
            }
        }
    }

    public enum PrioritySource {
        USER,
        AI_DEFAULT
    }
}
