package com.stockit.backend.feature.strategy.dto.response;

import java.util.List;

import com.stockit.backend.feature.strategy.approval.StrategyReviewStatus;
import com.stockit.backend.feature.strategy.domain.StrategyCaseStatus;

/** 최종 전략 영속화와 Reviewer별 Teams 전송 결과. */
public record AiStrategyTeamsRequestResponse(
        Long strategyCaseId,
        String selectedOptionId,
        Long strategyOptionId,
        Long finalSelectionId,
        StrategyCaseStatus caseStatus,
        DeliveryStatus deliveryStatus,
        List<ReviewerDelivery> reviewers
) {
    public AiStrategyTeamsRequestResponse {
        reviewers = List.copyOf(reviewers);
    }

    public enum DeliveryStatus {
        SENT,
        PARTIAL_FAILED,
        FAILED
    }

    public record ReviewerDelivery(
            Long reviewerId,
            String reviewerName,
            String email,
            StrategyReviewStatus deliveryStatus,
            String failureCode
    ) {
    }
}
