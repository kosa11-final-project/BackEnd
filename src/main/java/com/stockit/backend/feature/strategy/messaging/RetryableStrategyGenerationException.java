package com.stockit.backend.feature.strategy.messaging;

import com.stockit.backend.feature.strategy.domain.StrategyGenerationStage;

/**
 * 같은 Case를 지연 재시도하면 성공할 수 있는 전략 생성 오류
 */
public class RetryableStrategyGenerationException extends RuntimeException {

    private final String failureCode;
    private final StrategyGenerationStage expectedStage;

    public RetryableStrategyGenerationException(
            String failureCode,
            StrategyGenerationStage expectedStage,
            String message
    ) {
        this(failureCode, expectedStage, message, null);
    }

    public RetryableStrategyGenerationException(
            String failureCode,
            StrategyGenerationStage expectedStage,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.failureCode = failureCode;
        this.expectedStage = expectedStage;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public StrategyGenerationStage getExpectedStage() {
        return expectedStage;
    }
}
