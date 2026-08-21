package com.stockit.backend.feature.strategy.service;

import com.stockit.backend.feature.strategy.messaging.StrategyGenerationJobMessage;

/**
 * 일시 실패한 전략 생성 작업을 지연 재시도 Queue에 전달하는 포트
 */
public interface StrategyGenerationRetryPublisher {

    void publishForRetry(StrategyGenerationJobMessage message, int retryCount);
}
