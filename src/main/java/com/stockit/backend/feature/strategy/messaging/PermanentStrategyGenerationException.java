package com.stockit.backend.feature.strategy.messaging;

import com.stockit.backend.feature.strategy.domain.StrategyGenerationStage;

/**
 * 같은 메시지를 다시 처리해도 성공할 수 없어 DLQ로 격리해야 하는 오류
 */
public class PermanentStrategyGenerationException extends RuntimeException {

    private final String failureCode;
    private final StrategyGenerationStage expectedStage;

    public PermanentStrategyGenerationException(String failureCode, String message) {
        this(failureCode, null, message, null);
    }

    public PermanentStrategyGenerationException(
            String failureCode,
            StrategyGenerationStage expectedStage,
            String message
    ) {
        this(failureCode, expectedStage, message, null);
    }

    public PermanentStrategyGenerationException(
            String failureCode,
            String message,
            Throwable cause
    ) {
        this(failureCode, null, message, cause);
    }

    public PermanentStrategyGenerationException(
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
