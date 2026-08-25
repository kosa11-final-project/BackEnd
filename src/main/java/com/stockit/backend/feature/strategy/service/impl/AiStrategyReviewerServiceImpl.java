package com.stockit.backend.feature.strategy.service.impl;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.strategy.dto.response.AiStrategyReviewerListResponse;
import com.stockit.backend.feature.strategy.mapper.AiStrategyReviewerMapper;
import com.stockit.backend.feature.strategy.service.AiStrategyReviewerService;
import com.stockit.backend.feature.strategy.vo.AiStrategyReviewerVO;

@Service
public class AiStrategyReviewerServiceImpl implements AiStrategyReviewerService {

    private final AiStrategyReviewerMapper reviewerMapper;

    public AiStrategyReviewerServiceImpl(AiStrategyReviewerMapper reviewerMapper) {
        this.reviewerMapper = reviewerMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public AiStrategyReviewerListResponse findAll(Long organizationId) {
        validateOrganization(organizationId);
        return AiStrategyReviewerListResponse.from(
                reviewerMapper.selectAvailableReviewers(organizationId)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<AiStrategyReviewerVO> requireReviewers(
            Long organizationId,
            List<Long> reviewerIds,
            int maximumReviewers
    ) {
        validateOrganization(organizationId);
        if (reviewerIds == null || reviewerIds.isEmpty()
                || reviewerIds.size() > maximumReviewers
                || reviewerIds.stream().anyMatch(id -> id == null || id <= 0)
                || new HashSet<>(reviewerIds).size() != reviewerIds.size()) {
            throw new AppException(ErrorCode.AI_STRATEGY_INVALID_REVIEWERS);
        }

        List<AiStrategyReviewerVO> reviewers =
                reviewerMapper.selectAvailableReviewersByIds(
                        organizationId, reviewerIds
                );
        if (reviewers.size() != reviewerIds.size()) {
            throw new AppException(ErrorCode.AI_STRATEGY_REVIEWER_NOT_FOUND);
        }
        Set<String> normalizedEmails = new HashSet<>();
        boolean duplicateEmail = reviewers.stream()
                .map(AiStrategyReviewerVO::getEmail)
                .map(email -> email.trim().toLowerCase(Locale.ROOT))
                .anyMatch(email -> !normalizedEmails.add(email));
        if (duplicateEmail) {
            throw new AppException(ErrorCode.AI_STRATEGY_INVALID_REVIEWERS);
        }
        return List.copyOf(reviewers);
    }

    private static void validateOrganization(Long organizationId) {
        if (organizationId == null || organizationId <= 0) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
    }
}
