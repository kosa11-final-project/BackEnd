package com.stockit.backend.feature.strategy.forecast;

public record ForecastLock(
        String key,
        String ownerToken
) {
}
