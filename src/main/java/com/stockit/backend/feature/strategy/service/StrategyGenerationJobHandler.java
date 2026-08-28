package com.stockit.backend.feature.strategy.service;

import com.stockit.backend.feature.strategy.messaging.StrategyGenerationJobMessage;
import com.stockit.backend.feature.strategy.messaging.StrategyGenerationAttempt;

/**
 * RabbitMQ Adapter가 전달한 전략 생성 작업을 수행하는 애플리케이션 포트
 */
public interface StrategyGenerationJobHandler {

    void handle(
            StrategyGenerationJobMessage message,
            StrategyGenerationAttempt attempt
    );

    default void handle(StrategyGenerationJobMessage message) {
        handle(message, StrategyGenerationAttempt.standalone());
    }
}
