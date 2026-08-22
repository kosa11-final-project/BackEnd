package com.stockit.backend.feature.demandforecast.domain;

/** 실행 상태 커밋 이후 관리자 알림으로 변환되는 수요예측 파이프라인 이벤트입니다. */
public record DemandForecastRunNotificationEvent(
        Long forecastRunId,
        String notificationType,
        String severity,
        String title,
        String message,
        String deduplicationKey
) {
}
