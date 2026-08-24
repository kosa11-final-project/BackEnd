package com.stockit.backend.feature.strategy.result;

public class InvalidStrategyResultException extends RuntimeException {
    public InvalidStrategyResultException(String message, Throwable cause) {
        super(message, cause);
    }
}
