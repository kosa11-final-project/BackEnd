package com.stockit.backend.feature.strategy.forecast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.stockit.backend.feature.strategy.messaging.PermanentStrategyGenerationException;

class StrategyForecastResponseValidatorTest {

    private final StrategyForecastResponseValidator validator =
            new StrategyForecastResponseValidator();

    @Test
    void acceptsCompleteStrictlyOrderedResponse() {
        assertThatCode(() -> validator.validate(context(), validResponse()))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingSalesPointInsteadOfAcceptingPartialSuccess() {
        StrategyForecastResponse invalid = copyWithForecasts(
                List.of(validResponse().salesPointForecasts().get(0))
        );

        assertInvalid(invalid);
    }

    @Test
    void rejectsMissingDate() {
        StrategyForecastResponse valid = validResponse();
        SalesPointForecast first = new SalesPointForecast(
                10L,
                true,
                List.of(valid.salesPointForecasts().get(0).futureDailyPredictions().get(0))
        );
        assertInvalid(copyWithForecasts(List.of(
                first,
                valid.salesPointForecasts().get(1)
        )));
    }

    @Test
    void rejectsNegativeQuantity() {
        StrategyForecastResponse valid = validResponse();
        SalesPointForecast first = new SalesPointForecast(
                10L,
                true,
                List.of(
                        new DailyForecastPrediction(
                                LocalDate.of(2026, 8, 20),
                                new BigDecimal("-0.1")
                        ),
                        valid.salesPointForecasts().get(0)
                                .futureDailyPredictions().get(1)
                )
        );
        assertInvalid(copyWithForecasts(List.of(
                first,
                valid.salesPointForecasts().get(1)
        )));
    }

    @Test
    void rejectsUnsortedSalesPoints() {
        StrategyForecastResponse valid = validResponse();
        assertInvalid(copyWithForecasts(List.of(
                valid.salesPointForecasts().get(1),
                valid.salesPointForecasts().get(0)
        )));
    }

    @Test
    void rejectsNullResponse() {
        assertInvalid(null);
    }

    @Test
    void rejectsMismatchedForecastDays() {
        assertInvalid(copyWithMetadata(
                1,
                "forecast-run-1",
                3L,
                List.of(20L)
        ));
    }

    @Test
    void rejectsNonPositiveModelVersionId() {
        assertInvalid(copyWithMetadata(
                2,
                "forecast-run-1",
                0L,
                List.of(20L)
        ));
    }

    @Test
    void rejectsBlankForecastRunId() {
        assertInvalid(copyWithMetadata(
                2,
                " ",
                3L,
                List.of(20L)
        ));
    }

    @Test
    void rejectsCandidateSalesPointEchoMismatch() {
        assertInvalid(copyWithMetadata(
                2,
                "forecast-run-1",
                3L,
                List.of(30L)
        ));
    }

    private void assertInvalid(StrategyForecastResponse response) {
        assertThatThrownBy(() -> validator.validate(context(), response))
                .isInstanceOfSatisfying(
                        PermanentStrategyGenerationException.class,
                        exception -> assertThat(exception.getFailureCode())
                                .isEqualTo("FORECAST_RESPONSE_INVALID")
                );
    }

    private static StrategyForecastRequestContext context() {
        return new StrategyForecastRequestContext(
                new StrategyForecastRequest(
                        12345L,
                        1001L,
                        10L,
                        List.of(20L),
                        LocalDate.of(2026, 8, 20),
                        LocalDate.of(2026, 8, 21)
                ),
                List.of(10L, 20L),
                "request-hash"
        );
    }

    private static StrategyForecastResponse validResponse() {
        List<DailyForecastPrediction> firstSeries = List.of(
                prediction(20, "14.1"),
                prediction(21, "13.5")
        );
        List<DailyForecastPrediction> secondSeries = List.of(
                prediction(20, "18.4"),
                prediction(21, "17.9")
        );
        return new StrategyForecastResponse(
                12345L,
                1001L,
                10L,
                List.of(20L),
                LocalDate.of(2026, 8, 20),
                LocalDate.of(2026, 8, 21),
                2,
                "forecast-run-1",
                3L,
                OffsetDateTime.parse("2026-08-20T10:15:30+09:00"),
                List.of(
                        new SalesPointForecast(10L, true, firstSeries),
                        new SalesPointForecast(20L, false, secondSeries)
                )
        );
    }

    private static StrategyForecastResponse copyWithForecasts(
            List<SalesPointForecast> forecasts
    ) {
        StrategyForecastResponse source = validResponse();
        return new StrategyForecastResponse(
                source.strategyRequestId(),
                source.skuId(),
                source.sourceSalesPointId(),
                source.requestedCandidateSalesPointIds(),
                source.forecastStartDate(),
                source.forecastEndDate(),
                source.forecastDays(),
                source.forecastRunId(),
                source.modelVersionId(),
                source.forecastGeneratedAt(),
                forecasts
        );
    }

    private static StrategyForecastResponse copyWithMetadata(
            Integer forecastDays,
            String forecastRunId,
            Long modelVersionId,
            List<Long> requestedCandidateSalesPointIds
    ) {
        StrategyForecastResponse source = validResponse();
        return new StrategyForecastResponse(
                source.strategyRequestId(),
                source.skuId(),
                source.sourceSalesPointId(),
                requestedCandidateSalesPointIds,
                source.forecastStartDate(),
                source.forecastEndDate(),
                forecastDays,
                forecastRunId,
                modelVersionId,
                source.forecastGeneratedAt(),
                source.salesPointForecasts()
        );
    }

    private static DailyForecastPrediction prediction(int day, String quantity) {
        return new DailyForecastPrediction(
                LocalDate.of(2026, 8, day),
                new BigDecimal(quantity)
        );
    }
}
