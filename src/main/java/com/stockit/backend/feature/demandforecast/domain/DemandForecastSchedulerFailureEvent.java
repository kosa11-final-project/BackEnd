package com.stockit.backend.feature.demandforecast.domain;

import java.time.Instant;
import java.time.LocalDate;

/** 수요예측 Run 생성 전 또는 상태 조회 스케줄 자체에서 발생한 실패입니다. */
public record DemandForecastSchedulerFailureEvent(
        String schedulerName,
        LocalDate baseDate,
        String errorCode,
        String errorMessage,
        String deduplicationKey,
        Instant occurredAt
) {
}
