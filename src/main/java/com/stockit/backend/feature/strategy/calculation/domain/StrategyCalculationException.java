package com.stockit.backend.feature.strategy.calculation.domain;

/** 계산 입력을 신뢰할 수 없어 기준 시뮬레이션을 생성할 수 없는 경우. */
public class StrategyCalculationException extends RuntimeException {

    private final String code;

    public StrategyCalculationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public StrategyCalculationException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
