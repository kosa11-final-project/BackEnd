package com.stockit.backend.feature.inventory.dto.response;

public record RiskResponse(
        String assessmentStatus,
        String grade,
        String reason
) {
}
