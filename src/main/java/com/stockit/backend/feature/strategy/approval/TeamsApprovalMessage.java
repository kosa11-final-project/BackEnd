package com.stockit.backend.feature.strategy.approval;

/** Reviewer 한 명에게 전달할 최종 전략 카드 입력. */
public record TeamsApprovalMessage(
        Long reviewRequestId,
        String deliveryKey,
        String recipientEmail,
        TeamsApprovalCardData cardData
) {
}
