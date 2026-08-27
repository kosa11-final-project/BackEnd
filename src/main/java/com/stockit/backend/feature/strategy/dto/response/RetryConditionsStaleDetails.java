package com.stockit.backend.feature.strategy.dto.response;

import java.time.LocalDate;

/** 소비기한·판매중단일 등으로 기존 기간을 실행할 수 없는 재시도 오류 정보. */
public record RetryConditionsStaleDetails(
        String reason,
        LocalDate maximumEndDate,
        LocalDate preferredEndDate
) {
}
