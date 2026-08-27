package com.stockit.backend.feature.strategy.notification;

import java.time.LocalDateTime;
import java.util.UUID;

import com.stockit.backend.feature.strategy.domain.StrategyCaseStatus;
import com.stockit.backend.feature.strategy.domain.StrategyGenerationStage;

/** 브라우저가 목록·상세 재조회를 결정하는 최소 SSE 상태 신호 */
public record AiStrategySseEventPayload(
        UUID eventId,
        Long strategyCaseId,
        String caseName,
        StrategyCaseStatus caseStatus,
        StrategyGenerationStage generationStage,
        LocalDateTime occurredAt
) {
}
