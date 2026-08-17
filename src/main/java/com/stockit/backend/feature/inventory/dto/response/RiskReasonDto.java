package com.stockit.backend.feature.inventory.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "위험 판정 세부 사유")
public record RiskReasonDto(
        @Schema(description = "사유 코드", example = "EXPIRY_CRITICAL")
        String code,

        @Schema(description = "사유 설명 메시지", example = "소비기한 30일 이하 임박 (22일 남음)")
        String message,

        @Schema(description = "심각도 (CRITICAL, WARNING, NORMAL, GOOD)", example = "CRITICAL")
        String severity,

        @Schema(description = "산출 근거 및 데이터 수치", example = "nearestExpiryDays=22")
        String evidence
) {
}
