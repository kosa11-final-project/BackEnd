package com.stockit.backend.feature.strategy.messaging;

/**
 * RabbitMQ가 메시지를 책임지고 전달할 수 있음을 확인하지 못한 경우
 */
public class StrategyGenerationPublishException extends RuntimeException {

    public StrategyGenerationPublishException(String message) {
        super(message);
    }

    public StrategyGenerationPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
