package com.stockit.backend.feature.strategy.forecast;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyForecastPrediction(
        LocalDate date,
        BigDecimal predictedQty
) {
}
