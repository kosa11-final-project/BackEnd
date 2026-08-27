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

        @Schema(description = "마지막 재고 동기화가 RISK_ASSESSMENT.reason_message에 저장한 대표 위험 사유", example = "소비기한 30일 이하 임박 (22일 남음)")
        String reasonMessage,

        @Schema(description = "평가 규칙 버전", example = "v1.1.0")
        String ruleVersion,

        @Schema(description = "판정 시각")
        Instant assessedAt,

        @Schema(description = "기준일", example = "2026-08-16")
        LocalDate baseDate,

        @Schema(description = "판매중지·소비기한 경과·소진 LOT를 제외한 현재 판매 가능 재고", example = "110")
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

        @Schema(description = "RISK_ASSESSMENT.holding_days에 저장된 최대 보유 일수(일)", example = "14")
        Integer maxHoldingDays,

        @Schema(description = "세부 위험 사유 및 근거 목록")
        List<RiskReasonDto> reasons,

        @Schema(description = "RISK_ASSESSMENT.stock_days에 저장된 30일 평균 예측수요 기준 예상 소진까지 남은 일수", example = "20.0")
        BigDecimal stockCoverageDays,

        @Schema(description = "현재 판매 가능 재고가 안전재고보다 적은지 여부 (Y, N)", example = "Y")
        String shortageYn
) {
    public RiskAssessmentDetailResponse {
        reasons = List.copyOf(reasons == null ? List.of() : reasons);
    }

    /** 기존 위험 응답 생성부와의 하위 호환용 생성자입니다. */
    public RiskAssessmentDetailResponse(
            String assessmentStatus,
            String riskGrade,
            String dbRiskGrade,
            String reasonMessage,
            String ruleVersion,
            Instant assessedAt,
            LocalDate baseDate,
            BigDecimal availableQty,
            BigDecimal shortageQty30,
            BigDecimal safetyGapQty,
            BigDecimal projectedD7,
            BigDecimal safetyStockQty,
            Integer nearestExpiryDays,
            Integer maxHoldingDays,
            List<RiskReasonDto> reasons
    ) {
        this(
                assessmentStatus,
                riskGrade,
                dbRiskGrade,
                reasonMessage,
                ruleVersion,
                assessedAt,
                baseDate,
                availableQty,
                shortageQty30,
                safetyGapQty,
                projectedD7,
                safetyStockQty,
                nearestExpiryDays,
                maxHoldingDays,
                reasons,
                null,
                null
        );
    }
}
