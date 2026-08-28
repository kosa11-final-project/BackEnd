package com.stockit.backend.feature.strategy.recommendation;

/** 서버 후보 평가 결과가 추천 단계에서 갖는 의미. */
public enum CandidateEvaluationDisposition {
    RECOMMENDABLE,
    NO_EXECUTABLE_STRATEGY,
    INPUT_DATA_UNAVAILABLE,
    SIMULATION_FAILED,
    INVALID_EVALUATION_RESULT
}
