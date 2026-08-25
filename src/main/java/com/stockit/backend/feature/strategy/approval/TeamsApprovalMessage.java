package com.stockit.backend.feature.strategy.approval;

/** Reviewer 한 명에게 전달할 최종 전략 카드 입력. */
public record TeamsApprovalMessage(
        String recipientEmail,
        Long strategyCaseId,
        String caseName,
        String skuCode,
        String skuName,
        String requesterName,
        ResolvedStrategySelection resolvedSelection
) {
}
