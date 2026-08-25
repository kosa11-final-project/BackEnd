package com.stockit.backend.feature.strategy.approval;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.stockit.backend.feature.strategy.calculation.candidate.policy.StrategyPeriodEligibilityPolicy.PeriodConstraints;
import com.stockit.backend.feature.strategy.calculation.domain.BaselineSimulation;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.result.StrategyGenerationResult;

/** DB Snapshot과 Teams 카드가 함께 사용하는 최종 서버 확정 전략. */
public record ResolvedStrategySelection(
        Long strategyCaseId,
        StrategyRecommendationSource recommendationSource,
        StrategySelectionInputSource inputSource,
        StrategyGenerationResult.Option option,
        StrategyCalculationContext calculationContext,
        BaselineSimulation baselineSimulation,
        BigDecimal targetQuantity,
        LocalDate businessDate,
        LocalDate evaluationEndDate,
        PeriodConstraints periodConstraints,
        String forecastRequestHash,
        String selectionFingerprint
) {
    public ResolvedStrategySelection {
        if (strategyCaseId == null || recommendationSource == null
                || inputSource == null || option == null
                || calculationContext == null || baselineSimulation == null
                || targetQuantity == null || targetQuantity.signum() <= 0
                || businessDate == null || evaluationEndDate == null
                || periodConstraints == null
                || selectionFingerprint == null || selectionFingerprint.isBlank()) {
            throw new IllegalArgumentException("resolved strategy selection is invalid");
        }
    }
}
