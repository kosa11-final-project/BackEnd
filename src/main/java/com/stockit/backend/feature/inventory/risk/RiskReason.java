package com.stockit.backend.feature.inventory.risk;

public record RiskReason(
        String code,
        String message,
        String severity, // CRITICAL, WARNING, NORMAL, INFO
        String evidence
) {
}
