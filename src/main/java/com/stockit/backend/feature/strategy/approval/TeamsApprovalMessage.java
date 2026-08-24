package com.stockit.backend.feature.strategy.approval;

import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.result.StrategyGenerationResult;

/** Reviewer 한 명에게 전달할 최종 전략 카드 입력. */
public record TeamsApprovalMessage(
        String recipientEmail,
        Long strategyCaseId,
        String caseName,
        String skuCode,
        String skuName,
        String requesterName,
        StrategyGenerationResult.Option option,
        StrategyCalculationContext calculationContext
) {
}
