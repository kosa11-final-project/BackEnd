package com.stockit.backend.feature.strategy.calculation.candidate.domain;

/** 후보 계산에서 데이터 부재 또는 이번 구현 범위 때문에 적용한 명시적 가정. */
public enum CandidateAssumption {
    SAFETY_STOCK_DEFAULTED_TO_ZERO,
    TRANSFER_COST_EXCLUDED,
    DISCOUNT_DEMAND_UPLIFT_NOT_APPLIED,
    TARGET_COMMERCIAL_TERMS_COPIED_FROM_SOURCE,
    INVENTORY_RESERVED_UNTIL_STRATEGY_START
}
