package com.stockit.backend.feature.inventory.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "SKU × 판매처 위험도 평가 상세 응답")
public record RiskAssessmentDetailResponse(
        @Schema(description = "판정 상태 (ASSESSED, UNASSESSED, STALE, FAILED, REASSESSING)", example = "ASSESSED")
        String assessmentStatus,

        @Schema(description = "API 위험 등급 (DANGER, CAUTION, NORMAL, SAFE)", example = "DANGER")
        String riskGrade,

        @Schema(description = "DB 내부 위험 등급 (CRITICAL, WARNING, NORMAL, GOOD)", example = "CRITICAL")
        String dbRiskGrade,

        @Schema(description = "대표 위험 사유 메시지", example = "소비기한 30일 이하 임박 (22일 남음)")
        String reasonMessage,

        @Schema(description = "평가 규칙 버전", example = "v1.1.0")
        String ruleVersion,

        @Schema(description = "판정 시각")
        Instant assessedAt,

        @Schema(description = "기준일", example = "2026-08-16")
        LocalDate baseDate,

        @Schema(description = "현재 가용재고", example = "110")
        BigDecimal availableQty,

        @Schema(description = "D+30 수요예측 부족 수량", example = "0")
        BigDecimal shortageQty30,

        @Schema(description = "안전재고 부족 수량", example = "0")
        BigDecimal safetyGapQty,

        @Schema(description = "D+7 시점 예상 가용재고", example = "60")
        BigDecimal projectedD7,

        @Schema(description = "안전재고 목표치", example = "30")
        BigDecimal safetyStockQty,

        @Schema(description = "가장 가까운 잔여 소비기한(일)", example = "22")
        Integer nearestExpiryDays,

        @Schema(description = "최대 보유 일수(일)", example = "14")
        Integer maxHoldingDays,

        @Schema(description = "세부 위험 사유 및 근거 목록")
        List<RiskReasonDto> reasons
) {
    public RiskAssessmentDetailResponse {
        reasons = List.copyOf(reasons == null ? List.of() : reasons);
    }
}
