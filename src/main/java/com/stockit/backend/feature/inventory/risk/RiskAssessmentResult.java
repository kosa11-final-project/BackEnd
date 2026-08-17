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
        Integer nearestExpiryDays,
        Integer maxHoldingDays,
        LocalDate baseDate,
        Instant assessedAt,
        String ruleVersion
) {
}
