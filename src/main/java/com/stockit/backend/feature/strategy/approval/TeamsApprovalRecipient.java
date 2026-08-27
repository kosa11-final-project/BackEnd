package com.stockit.backend.feature.strategy.approval;

/** 영속된 Reviewer 전송 단위와 현재 연락처 정보. */
public record TeamsApprovalRecipient(
        Long reviewRequestId,
        Long reviewerId,
        String reviewerName,
        String email,
        StrategyReviewStatus reviewStatus,
        boolean active
) {
    public boolean deliverable() {
        return active && email != null && !email.isBlank();
    }
}
