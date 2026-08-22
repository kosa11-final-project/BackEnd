package com.stockit.backend.feature.statistics.dto.request;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "AI 전략 통계 데모 이력 생성 요청. 날짜를 생략하면 종료일 기준 6개월입니다.")
public record StrategyStatisticsDemoBackfillRequest(
        LocalDate fromDate,
        LocalDate toDate
) {
}
