package com.stockit.backend.feature.strategy.service;

import com.stockit.backend.feature.strategy.messaging.StrategyGenerationJobMessage;

/**
 * 전략 생성 작업을 Main Queue에 전달하는 애플리케이션 포트
 */
public interface StrategyGenerationJobPublisher {

    void publish(StrategyGenerationJobMessage message);
}
