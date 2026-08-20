package com.stockit.backend.feature.strategy.domain;

import java.time.LocalDateTime;

/**
 * 전략 생성 Case가 트랜잭션 안에서 저장됐음을 알리는 내부 이벤트
 */
public record StrategyGenerationRequestedEvent(
        Long strategyCaseId,
        LocalDateTime requestedAt
) {
}
