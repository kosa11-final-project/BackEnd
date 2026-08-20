package com.stockit.backend.feature.strategy.domain;

/**
 * AI 전략 생성 요청부터 실행 완료까지의 처리 상태
 */
public enum StrategyCaseStatus {
    GENERATING,
    GENERATED,
    GENERATION_FAILED,
    READY_TO_EXECUTE,
    EXECUTING,
    EXECUTION_COMPLETED,
    EXPIRED
}
