package com.stockit.backend.feature.strategy.calculation.candidate.calculator;

import com.stockit.backend.feature.strategy.calculation.candidate.domain.CandidateExclusionReason;

/** RT_TRANSFER 경로·중량·요율 누락을 후보 제외 사유로 전달한다. */
public class InventoryTransferCostCalculationException extends RuntimeException {

    private final CandidateExclusionReason reason;

    public InventoryTransferCostCalculationException(
            CandidateExclusionReason reason,
            String message
    ) {
        super(message);
        this.reason = reason;
    }

    public CandidateExclusionReason getReason() {
        return reason;
    }
}
