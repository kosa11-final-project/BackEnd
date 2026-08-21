package com.stockit.backend.feature.strategy.forecast;

public class InvalidForecastCheckpointException extends RuntimeException {

    public InvalidForecastCheckpointException(String message) {
        super(message);
    }

    public InvalidForecastCheckpointException(String message, Throwable cause) {
        super(message, cause);
    }
}
