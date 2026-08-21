package com.stockit.backend.feature.strategy.forecast;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.stockit.backend.feature.strategy.domain.StrategyGenerationStage;
import com.stockit.backend.feature.strategy.messaging.PermanentStrategyGenerationException;

@Component
public class StrategyForecastResponseValidator {

    public void validate(
            StrategyForecastRequestContext context,
            StrategyForecastResponse response
    ) {
        StrategyForecastRequest request = context.request();
        List<Long> echoedCandidates = response == null
                || response.requestedCandidateSalesPointIds() == null
                ? List.of()
                : response.requestedCandidateSalesPointIds();
        int expectedDays = Math.toIntExact(ChronoUnit.DAYS.between(
                request.forecastStartDate(),
                request.forecastEndDate()
        ) + 1);

        if (response == null
                || !Objects.equals(response.strategyRequestId(), request.strategyRequestId())
                || !Objects.equals(response.skuId(), request.skuId())
                || !Objects.equals(response.sourceSalesPointId(), request.sourceSalesPointId())
                || !echoedCandidates.equals(request.candidateSalesPointIds())
                || !Objects.equals(response.forecastStartDate(), request.forecastStartDate())
                || !Objects.equals(response.forecastEndDate(), request.forecastEndDate())
                || !Objects.equals(response.forecastDays(), expectedDays)
                || response.forecastRunId() == null
                || response.forecastRunId().isBlank()
                || response.modelVersionId() == null
                || response.modelVersionId() <= 0
                || response.forecastGeneratedAt() == null
                || response.salesPointForecasts() == null) {
            fail("Demand forecast response metadata does not match the request");
        }

        validateSalesPointForecasts(context, response.salesPointForecasts(), expectedDays);
    }

    private static void validateSalesPointForecasts(
            StrategyForecastRequestContext context,
            List<SalesPointForecast> forecasts,
            int expectedDays
    ) {
        if (forecasts.stream().anyMatch(Objects::isNull)) {
            fail("Demand forecast sales point result must not be null");
        }
        List<Long> actualIds = forecasts.stream()
                .map(SalesPointForecast::salesPointId)
                .toList();
        if (actualIds.stream().anyMatch(Objects::isNull)
                || new HashSet<>(actualIds).size() != actualIds.size()
                || !actualIds.equals(context.expectedSalesPointIds())) {
            fail("Demand forecast sales point scope is missing, duplicated, extra, or unsorted");
        }

        Long sourceId = context.request().sourceSalesPointId();
        Set<LocalDate> expectedDates = new HashSet<>();
        for (int offset = 0; offset < expectedDays; offset++) {
            expectedDates.add(context.request().forecastStartDate().plusDays(offset));
        }

        for (SalesPointForecast forecast : forecasts) {
            boolean expectedSource = Objects.equals(sourceId, forecast.salesPointId());
            if (!Objects.equals(forecast.sourceSalesPoint(), expectedSource)) {
                fail("Demand forecast source sales point marker is invalid");
            }
            validateDailyPredictions(forecast, expectedDates, expectedDays);
        }
    }

    private static void validateDailyPredictions(
            SalesPointForecast forecast,
            Set<LocalDate> expectedDates,
            int expectedDays
    ) {
        List<DailyForecastPrediction> predictions = forecast.futureDailyPredictions();
        if (predictions == null || predictions.size() != expectedDays) {
            fail("Demand forecast daily series length is invalid");
        }
        LocalDate previous = null;
        Set<LocalDate> actualDates = new HashSet<>();
        for (DailyForecastPrediction prediction : predictions) {
            if (prediction == null
                    || prediction.date() == null
                    || prediction.predictedQty() == null
                    || prediction.predictedQty().signum() < 0
                    || (previous != null && !prediction.date().isAfter(previous))) {
                fail("Demand forecast daily series contains invalid values or ordering");
            }
            previous = prediction.date();
            actualDates.add(prediction.date());
        }
        if (actualDates.size() != expectedDays || !actualDates.equals(expectedDates)) {
            fail("Demand forecast daily series contains missing or unexpected dates");
        }
    }

    private static void fail(String message) {
        throw new PermanentStrategyGenerationException(
                "FORECAST_RESPONSE_INVALID",
                StrategyGenerationStage.FORECASTING,
                message
        );
    }
}
