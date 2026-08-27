package com.stockit.backend.feature.strategy.notification;

import java.time.LocalDateTime;

/** 연결 또는 재연결 직후 프론트가 상태를 재조회할 수 있도록 보내는 초기 신호 */
public record AiStrategySseConnectedPayload(
        String connectionId,
        LocalDateTime connectedAt
) {
}
