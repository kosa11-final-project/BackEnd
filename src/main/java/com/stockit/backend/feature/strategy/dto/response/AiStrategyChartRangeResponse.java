package com.stockit.backend.feature.strategy.dto.response;

import java.time.LocalDate;

/** 전체 예측 시계열에서 화면이 기본으로 표시할 선택 전략 기간. */
public record AiStrategyChartRangeResponse(
        LocalDate startDate,
        LocalDate endDate
) {
    public AiStrategyChartRangeResponse {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("chart range is invalid");
        }
    }
}
