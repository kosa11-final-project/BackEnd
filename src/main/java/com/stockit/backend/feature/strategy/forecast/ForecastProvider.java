package com.stockit.backend.feature.strategy.forecast;

public interface ForecastProvider {

    StrategyForecastResponse forecast(StrategyForecastRequest request);
}
