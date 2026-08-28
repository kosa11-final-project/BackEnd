package com.stockit.backend.feature.strategy.calculation.candidate.domain;

/** 판매처·전략 조합을 후보에서 제외한 기계 판독 가능한 사유. */
public enum CandidateExclusionReason {
    SAME_AS_SOURCE(CandidateExclusionImpact.BUSINESS_INELIGIBLE),
    TARGET_SALES_POINT_NOT_FOUND(CandidateExclusionImpact.INPUT_UNAVAILABLE),
    TARGET_PRICE_INCOMPLETE(CandidateExclusionImpact.INPUT_UNAVAILABLE),
    TARGET_ROUTE_NOT_FOUND(CandidateExclusionImpact.BUSINESS_INELIGIBLE),
    SHARED_WAREHOUSE_NOT_FOUND(CandidateExclusionImpact.BUSINESS_INELIGIBLE),
    PHYSICAL_TRANSFER_NOT_REQUIRED(CandidateExclusionImpact.BUSINESS_INELIGIBLE),
    SOURCE_STOCK_INSUFFICIENT(CandidateExclusionImpact.BUSINESS_INELIGIBLE),
    SOURCE_SAFETY_STOCK_VIOLATION(CandidateExclusionImpact.BUSINESS_INELIGIBLE),
    TARGET_ADDITIONAL_DEMAND_NOT_FOUND(CandidateExclusionImpact.BUSINESS_INELIGIBLE),
    TARGET_FORECAST_DEMAND_NOT_FOUND(CandidateExclusionImpact.BUSINESS_INELIGIBLE),
    LOT_NOT_SELLABLE_IN_PERIOD(CandidateExclusionImpact.BUSINESS_INELIGIBLE),
    SOURCE_SALES_POINT_REQUIRED(CandidateExclusionImpact.BUSINESS_INELIGIBLE),
    SOURCE_PRICE_INCOMPLETE(CandidateExclusionImpact.INPUT_UNAVAILABLE),
    MINIMUM_SELLING_PRICE_MISSING(CandidateExclusionImpact.INPUT_UNAVAILABLE),
    DISCOUNT_RATE_NOT_AVAILABLE(CandidateExclusionImpact.BUSINESS_INELIGIBLE),
    TARGET_ALREADY_LISTED(CandidateExclusionImpact.BUSINESS_INELIGIBLE),
    TARGET_NOT_LISTED(CandidateExclusionImpact.BUSINESS_INELIGIBLE),
    CHANNEL_TARGET_NOT_AVAILABLE(CandidateExclusionImpact.BUSINESS_INELIGIBLE),
    PUBLIC_UNASSIGNED_STRATEGY_NOT_SUPPORTED(
            CandidateExclusionImpact.BUSINESS_INELIGIBLE
    ),
    DUPLICATED_CANDIDATE(CandidateExclusionImpact.DIAGNOSTIC),
    TRANSFER_ROUTE_NOT_FOUND(CandidateExclusionImpact.BUSINESS_INELIGIBLE),
    TRANSFER_ROUTE_AMBIGUOUS(CandidateExclusionImpact.INPUT_UNAVAILABLE),
    TRANSFER_COST_POLICY_NOT_FOUND(CandidateExclusionImpact.INPUT_UNAVAILABLE),
    TRANSFER_COST_POLICY_AMBIGUOUS(CandidateExclusionImpact.INPUT_UNAVAILABLE),
    SKU_WEIGHT_NOT_FOUND(CandidateExclusionImpact.INPUT_UNAVAILABLE),
    SKU_WEIGHT_UNIT_UNSUPPORTED(CandidateExclusionImpact.INPUT_UNAVAILABLE);

    private final CandidateExclusionImpact impact;

    CandidateExclusionReason(CandidateExclusionImpact impact) {
        this.impact = impact;
    }

    public CandidateExclusionImpact impact() {
        return impact;
    }
}
