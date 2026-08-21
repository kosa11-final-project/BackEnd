package com.stockit.backend.feature.strategy.forecast;

import java.util.List;

public record SalesPointForecast(
        Long salesPointId,
        Boolean sourceSalesPoint,
        List<DailyForecastPrediction> futureDailyPredictions
) {
}
