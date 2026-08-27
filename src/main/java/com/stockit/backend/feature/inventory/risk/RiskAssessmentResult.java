package com.stockit.backend.feature.inventory.risk;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record RiskAssessmentResult(
        String assessmentStatus, // ASSESSED, UNASSESSED, STALE, FAILED, REASSESSING
        String dbRiskGrade,      // CRITICAL, WARNING, NORMAL, GOOD
        String apiRiskGrade,     // DANGER, CAUTION, NORMAL, SAFE
        String primaryReason,
        List<RiskReason> reasons,
        BigDecimal availableQty,
        BigDecimal shortageQty30,
        BigDecimal safetyGapQty,
        BigDecimal projectedD7,
        BigDecimal safetyStockQty,
        BigDecimal expectedDisposalQty30,
        BigDecimal expectedDisposalRate30,
        Integer nearestSaleEndDays,
        Integer nearestExpiryDays,
        Integer maxHoldingDays,
        LocalDate baseDate,
        Instant assessedAt,
        String ruleVersion
) {
    /** 현재 판매 가능 재고가 안전재고보다 작은지 판정합니다. */
    public boolean isCurrentStockUnderSafety() {
        if (safetyStockQty == null) {
            return false;
        }
        BigDecimal normalizedAvailableQty = availableQty == null ? BigDecimal.ZERO : availableQty;
        return normalizedAvailableQty.compareTo(safetyStockQty) < 0;
    }
}
