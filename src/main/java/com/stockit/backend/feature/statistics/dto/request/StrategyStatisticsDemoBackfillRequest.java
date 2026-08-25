package com.stockit.backend.feature.statistics.dto.request;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "AI 전략 통계 데모 이력 재생성 요청. 날짜를 생략하면 2025-08-24~2026-08-23입니다.")
public record StrategyStatisticsDemoBackfillRequest(
        LocalDate fromDate,
        LocalDate toDate
) {
}
