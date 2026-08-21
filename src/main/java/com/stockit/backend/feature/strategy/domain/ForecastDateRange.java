package com.stockit.backend.feature.strategy.domain;

import java.time.LocalDate;

/**
 * 사용자 희망일과 정책 기본값을 반영한 실제 수요예측 대상 기간
 */
public record ForecastDateRange(LocalDate startDate, LocalDate endDate) {
}
