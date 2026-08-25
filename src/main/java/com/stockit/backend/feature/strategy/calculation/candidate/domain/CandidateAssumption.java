package com.stockit.backend.feature.strategy.calculation.candidate.domain;

/** 후보 계산에서 데이터 부재 또는 이번 구현 범위 때문에 적용한 명시적 가정. */
public enum CandidateAssumption {
    SAFETY_STOCK_DEFAULTED_TO_ZERO,
    TRANSFER_COST_EXCLUDED,
    /** 3일 TTL 내 구버전 Redis 결과 역직렬화 호환용. 신규 후보에는 사용하지 않는다. */
    @Deprecated
    DISCOUNT_DEMAND_UPLIFT_NOT_APPLIED,
    TARGET_COMMERCIAL_TERMS_COPIED_FROM_SOURCE,
    INVENTORY_RESERVED_UNTIL_STRATEGY_START
}
