package com.stockit.backend.feature.strategy.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "동일 검색 조건이 적용된 전체 전략 실행 KPI")
public record StrategyExecutionSummaryResponse(
        @Schema(description = "실행 대기(READY)를 제외한 전략 수", example = "281")
        long executionStrategyCount,
        @Schema(description = "실행 중(EXECUTING)인 전략 수", example = "24")
        long inProgressStrategyCount,
        @Schema(description = "최신 실행 결과가 PARTIAL인 확인 필요 전략 수", example = "7")
        long attentionStrategyCount,
        @Schema(description = "동일 검색 조건에 해당하는 전체 전략 수", example = "362")
        long totalStrategyCount
) {
}
