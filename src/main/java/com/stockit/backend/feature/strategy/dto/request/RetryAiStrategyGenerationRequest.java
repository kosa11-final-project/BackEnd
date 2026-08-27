package com.stockit.backend.feature.strategy.dto.request;

import com.stockit.backend.feature.strategy.domain.StrategyRetryDateAdjustmentPolicy;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "실패한 AI 전략 생성 Case의 사용자 재시도 요청")
public record RetryAiStrategyGenerationRequest(
        @Schema(
                description = "과거 사용자 지정 시작일 처리 정책. 생략하면 REJECT",
                defaultValue = "REJECT",
                allowableValues = {"REJECT", "ADJUST_TO_TODAY"}
        )
        StrategyRetryDateAdjustmentPolicy dateAdjustmentPolicy
) {
    public StrategyRetryDateAdjustmentPolicy effectiveDateAdjustmentPolicy() {
        return dateAdjustmentPolicy == null
                ? StrategyRetryDateAdjustmentPolicy.REJECT
                : dateAdjustmentPolicy;
    }
}
