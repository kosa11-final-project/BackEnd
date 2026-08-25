package com.stockit.backend.feature.strategy.dto.response;

import java.util.List;

import com.stockit.backend.feature.strategy.vo.AiStrategyReviewerVO;

/** 동일 조직에서 Teams 수신인으로 선택할 수 있는 사용자 목록. */
public record AiStrategyReviewerListResponse(List<Reviewer> reviewers) {

    public AiStrategyReviewerListResponse {
        reviewers = List.copyOf(reviewers);
    }

    public static AiStrategyReviewerListResponse from(
            List<AiStrategyReviewerVO> reviewers
    ) {
        return new AiStrategyReviewerListResponse(reviewers.stream()
                .map(Reviewer::from)
                .toList());
    }

    public record Reviewer(
            Long reviewerId,
            String reviewerName,
            String email,
            Long organizationId,
            String organizationName,
            String roleName
    ) {
        private static Reviewer from(AiStrategyReviewerVO reviewer) {
            return new Reviewer(
                    reviewer.getReviewerId(),
                    reviewer.getReviewerName(),
                    reviewer.getEmail(),
                    reviewer.getOrganizationId(),
                    reviewer.getOrganizationName(),
                    reviewer.getRoleName()
            );
        }
    }
}
