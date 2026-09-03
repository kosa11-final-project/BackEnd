package com.stockit.backend.feature.strategy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.strategy.domain.ForecastDateRange;

class StrategyForecastDateRangeResolverTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 17);

    private final StrategyForecastDateRangeResolver resolver =
            new StrategyForecastDateRangeResolver();

    @Test
    void resolvesDefaultRangeAsInclusive30Days() {
        assertThat(resolver.resolve(null, null, TODAY))
                .isEqualTo(new ForecastDateRange(TODAY, TODAY.plusDays(29)));
    }

    @Test
    void includesPreStrategyDemandWhenOnlyFutureStartIsRequested() {
        LocalDate startDate = TODAY.plusDays(10);

        assertThat(resolver.resolve(startDate, null, TODAY))
                .isEqualTo(new ForecastDateRange(TODAY, TODAY.plusDays(29)));
    }

    @Test
    void resolvesEndOnlyWithoutProducingPastStartDate() {
        LocalDate nearEndDate = TODAY.plusDays(10);
        LocalDate maxEndDate = TODAY.plusDays(29);

        assertThat(resolver.resolve(null, nearEndDate, TODAY))
                .isEqualTo(new ForecastDateRange(TODAY, nearEndDate));
        assertThat(resolver.resolve(null, maxEndDate, TODAY))
                .isEqualTo(new ForecastDateRange(TODAY, maxEndDate));
    }

    @Test
    void includesPreStrategyDemandForExplicitFutureRange() {
        assertThat(resolver.resolve(TODAY.plusDays(1), TODAY.plusDays(29), TODAY))
                .isEqualTo(new ForecastDateRange(TODAY, TODAY.plusDays(29)));
    }

    @Test
    void rejectsStartAfterEndWithSpecificError() {
        assertError(
                () -> resolver.resolve(TODAY.plusDays(2), TODAY.plusDays(1), TODAY),
                ErrorCode.AI_STRATEGY_START_AFTER_END
        );
    }

    @Test
    void rejectsStartOutsideAllowedWindow() {
        assertError(
                () -> resolver.resolve(TODAY.minusDays(1), null, TODAY),
                ErrorCode.AI_STRATEGY_DATE_OUT_OF_RANGE
        );
        assertError(
                () -> resolver.resolve(TODAY.plusDays(30), null, TODAY),
                ErrorCode.AI_STRATEGY_DATE_OUT_OF_RANGE
        );
    }

    @Test
    void rejectsRangeLongerThanInclusive30Days() {
        assertError(
                () -> resolver.resolve(TODAY, TODAY.plusDays(30), TODAY),
                ErrorCode.AI_STRATEGY_DATE_OUT_OF_RANGE
        );
    }

    @Test
    void rejectsExplicitEndOutsideForecastHorizonEvenWhenRangeIsShort() {
        assertError(
                () -> resolver.resolve(TODAY.plusDays(20), TODAY.plusDays(30), TODAY),
                ErrorCode.AI_STRATEGY_DATE_OUT_OF_RANGE
        );
    }

    @Test
    void rejectsEndOnlyOutsideForecastWindow() {
        assertError(
                () -> resolver.resolve(null, TODAY.minusDays(1), TODAY),
                ErrorCode.AI_STRATEGY_DATE_OUT_OF_RANGE
        );
        assertError(
                () -> resolver.resolve(null, TODAY.plusDays(30), TODAY),
                ErrorCode.AI_STRATEGY_DATE_OUT_OF_RANGE
        );
    }

    private static void assertError(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }
}
