package com.stockit.backend.feature.strategy.dto.response;

import java.time.LocalDate;

/** 과거 시작일 재시도에 대한 사용자 확인 팝업 구성 정보. */
public record RetryDateAdjustmentRequiredDetails(
        String reason,
        LocalDate originalPreferredStartDate,
        LocalDate originalPreferredEndDate,
        LocalDate adjustedPreferredStartDate,
        LocalDate adjustedPreferredEndDate
) {
}
