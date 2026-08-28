package com.stockit.backend.feature.inventory.risk;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record RiskAssessmentResult(
        String assessmentStatus, // ASSESSED or UNASSESSED
        String dbRiskGrade,      // CRITICAL, WARNING, NORMAL, GOOD
        String apiRiskGrade,     // DB와 동일: CRITICAL, WARNING, NORMAL, GOOD
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
        String ruleVersion,
        String forecastUsability,
        BigDecimal projectedD60,
        BigDecimal projectedD90,
        BigDecimal expectedDisposalQty90,
        BigDecimal mediumTermDisposalQty90,
        BigDecimal mediumTermDisposalRate90,
        Integer mediumTermSaleEndDays,
        BigDecimal longTermOverstockQty60,
        BigDecimal longTermOverstockQty90,
        BigDecimal longTermOverstockRate90
) {
    /** Existing v1.6 result constructor retained for source compatibility. */
    public RiskAssessmentResult(
            String assessmentStatus,
            String dbRiskGrade,
            String apiRiskGrade,
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
        this(assessmentStatus, dbRiskGrade, apiRiskGrade, primaryReason, reasons, availableQty, shortageQty30,
                safetyGapQty, projectedD7, safetyStockQty, expectedDisposalQty30, expectedDisposalRate30,
                nearestSaleEndDays, nearestExpiryDays, maxHoldingDays, baseDate, assessedAt, ruleVersion,
                null, null, null, null, null, null, null, null, null, null);
    }

    /** 현재 판매 가능 재고가 안전재고보다 작은지 판정합니다. */
    public boolean isCurrentStockUnderSafety() {
        if (safetyStockQty == null) {
            return false;
        }
        BigDecimal normalizedAvailableQty = availableQty == null ? BigDecimal.ZERO : availableQty;
        return normalizedAvailableQty.compareTo(safetyStockQty) < 0;
    }

    /** Alias used by risk-card consumers for the D+60 overstock signal. */
    public BigDecimal excessQty60() {
        return longTermOverstockQty60;
    }

    /** Alias used by risk-card consumers for the D+90 overstock signal. */
    public BigDecimal excessQty90() {
        return longTermOverstockQty90;
    }

    public BigDecimal longTermExcessQty60() {
        return longTermOverstockQty60;
    }

    public BigDecimal longTermExcessQty90() {
        return longTermOverstockQty90;
    }

    public BigDecimal longTermExcessRate90() {
        return longTermOverstockRate90;
    }

    public BigDecimal mediumTermQty90() {
        return mediumTermDisposalQty90;
    }
}
