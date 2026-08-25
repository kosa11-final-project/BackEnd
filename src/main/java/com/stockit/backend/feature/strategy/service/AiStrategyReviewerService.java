package com.stockit.backend.feature.strategy.service;

import java.util.List;

import com.stockit.backend.feature.strategy.dto.response.AiStrategyReviewerListResponse;
import com.stockit.backend.feature.strategy.vo.AiStrategyReviewerVO;

public interface AiStrategyReviewerService {

    AiStrategyReviewerListResponse findAll(Long organizationId);

    List<AiStrategyReviewerVO> requireReviewers(
            Long organizationId,
            List<Long> reviewerIds,
            int maximumReviewers
    );
}
