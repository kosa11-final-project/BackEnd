package com.stockit.backend.feature.strategy.calculation.candidate.domain;

/** 후보 제외가 전체 추천 가능성 판단에 미치는 의미. */
public enum CandidateExclusionImpact {
    /** 계산은 완료됐지만 업무 조건상 해당 조합을 실행할 수 없음. */
    BUSINESS_INELIGIBLE,
    /** 전략을 평가하는 데 필요한 기준 정보가 없거나 모호함. */
    INPUT_UNAVAILABLE,
    /** 중복 제거처럼 결과 판정의 직접 근거가 아닌 진단 정보. */
    DIAGNOSTIC
}
