package com.stockit.backend.feature.strategy.dto.response;

import java.time.LocalDate;

/** 기존 사용자 지정 판매 기간 전체가 지난 재시도 오류 정보. */
public record RetryPeriodExpiredDetails(
        LocalDate originalPreferredStartDate,
        LocalDate originalPreferredEndDate,
        LocalDate currentDate
) {
}
