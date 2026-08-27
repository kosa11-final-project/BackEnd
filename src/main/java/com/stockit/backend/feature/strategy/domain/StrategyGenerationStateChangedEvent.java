package com.stockit.backend.feature.strategy.domain;

/**
 * 생성 Case의 조건부 상태 전이가 성공한 뒤 SSE 통지에 전달하는 최소 도메인 이벤트
 *
 * <p>사용자·표시 정보는 트랜잭션 커밋 이후 Listener가 Case에서 조회한다. 상태와
 * 단계는 후속 전이가 먼저 일어나도 원래 발생한 이벤트 의미가 바뀌지 않도록 함께 보존한다.</p>
 */
public record StrategyGenerationStateChangedEvent(
        Long strategyCaseId,
        StrategyCaseStatus caseStatus,
        StrategyGenerationStage generationStage
) {
}
