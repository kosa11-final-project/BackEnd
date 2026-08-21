package com.stockit.backend.feature.strategy.domain;

import java.time.LocalDateTime;

/**
 * 전략 생성 Case의 저장 완료를 커밋 이후 메시지 발행 흐름에 연결하는 내부 이벤트
 *
 * <p>이벤트 자체는 저장 트랜잭션 안에서 발행되지만 실제 RabbitMQ 발행은
 * {@code AFTER_COMMIT} Listener가 담당</p>
 */
public record StrategyGenerationRequestedEvent(
        Long strategyCaseId,
        LocalDateTime requestedAt
) {
}
