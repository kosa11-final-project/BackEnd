package com.stockit.backend.feature.strategy.calculation.engine;

/** 입력 Context는 유효하지만 특정 후보의 액션만 시뮬레이션할 수 없는 경우. */
public class CandidateSimulationException extends RuntimeException {

    private final String code;

    public CandidateSimulationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public CandidateSimulationException(
            String code,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
