package com.stockit.backend.feature.strategy.recommendation;

import com.stockit.backend.feature.strategy.calculation.domain.StrategyCandidateEvaluationResult;

/** 정량 평가가 끝난 후보를 LLM 입력 상한 안에서 결정적으로 선별한다. */
public interface RecommendationCandidatePreselector {

    RecommendationCandidateSelection select(StrategyCandidateEvaluationResult evaluation);
}
