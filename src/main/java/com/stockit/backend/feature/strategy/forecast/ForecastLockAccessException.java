package com.stockit.backend.feature.strategy.forecast;

public class ForecastLockAccessException extends RuntimeException {

    public ForecastLockAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
