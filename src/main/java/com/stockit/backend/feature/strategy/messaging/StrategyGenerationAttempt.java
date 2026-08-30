package com.stockit.backend.feature.strategy.messaging;

/** 메시지 재전달 횟수를 애플리케이션 계층에 명시적으로 전달한다. */
public record StrategyGenerationAttempt(int attemptNumber, int maximumAttempts) {

    public StrategyGenerationAttempt {
        if (attemptNumber < 1 || maximumAttempts < 1
                || attemptNumber > maximumAttempts) {
            throw new IllegalArgumentException("strategy generation attempt is invalid");
        }
    }

    /** 재시도 조정자 없이 직접 실행할 때는 현재 호출을 마지막 시도로 취급한다. */
    public static StrategyGenerationAttempt standalone() {
        return new StrategyGenerationAttempt(1, 1);
    }

    public boolean isFinalAttempt() {
        return attemptNumber >= maximumAttempts;
    }
}
