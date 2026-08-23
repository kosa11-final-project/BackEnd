package com.stockit.backend.feature.strategy.calculation.candidate.domain;

/** 판매처·전략 조합을 후보에서 제외한 기계 판독 가능한 사유. */
public enum CandidateExclusionReason {
    SAME_AS_SOURCE,
    TARGET_PRICE_INCOMPLETE,
    TARGET_ROUTE_NOT_FOUND,
    SHARED_WAREHOUSE_NOT_FOUND,
    PHYSICAL_TRANSFER_NOT_REQUIRED,
    SOURCE_STOCK_INSUFFICIENT,
    SOURCE_SAFETY_STOCK_VIOLATION,
    TARGET_ADDITIONAL_DEMAND_NOT_FOUND,
    LOT_NOT_SELLABLE_IN_PERIOD,
    DUPLICATED_CANDIDATE
}
