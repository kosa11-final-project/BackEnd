package com.stockit.backend.feature.strategy.messaging;

/**
 * 같은 메시지를 다시 처리해도 성공할 수 없어 DLQ로 격리해야 하는 오류
 */
public class PermanentStrategyGenerationException extends RuntimeException {

    private final String failureCode;

    public PermanentStrategyGenerationException(String failureCode, String message) {
        super(message);
        this.failureCode = failureCode;
    }

    public PermanentStrategyGenerationException(
            String failureCode,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.failureCode = failureCode;
    }

    public String getFailureCode() {
        return failureCode;
    }
}
