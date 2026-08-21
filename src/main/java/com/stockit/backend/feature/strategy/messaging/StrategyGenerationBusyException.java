package com.stockit.backend.feature.strategy.messaging;

/**
 * 다른 Worker가 같은 Case의 현재 단계를 처리 중임을 나타내는 정상 경합 신호
 */
public class StrategyGenerationBusyException extends RuntimeException {

    public StrategyGenerationBusyException(String message) {
        super(message);
    }
}
