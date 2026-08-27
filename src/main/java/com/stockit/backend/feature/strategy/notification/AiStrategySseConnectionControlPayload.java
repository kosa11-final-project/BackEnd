package com.stockit.backend.feature.strategy.notification;

import java.time.LocalDateTime;

/** 브라우저가 자동 재연결을 중단해야 하는 SSE 연결 제어 정보 */
public record AiStrategySseConnectionControlPayload(
        String reason,
        int maxConnections,
        LocalDateTime occurredAt
) {
}
