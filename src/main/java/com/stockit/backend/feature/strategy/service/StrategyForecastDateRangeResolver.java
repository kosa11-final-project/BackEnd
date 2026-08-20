package com.stockit.backend.feature.strategy.service;

import java.time.LocalDate;

import org.springframework.stereotype.Component;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.strategy.domain.ForecastDateRange;

/**
 * 사용자 희망일 조합에 따라 실제 수요예측 기간을 계산하고 정책 범위를 검증하는 컴포넌트
 */
@Component
public class StrategyForecastDateRangeResolver {

    private static final long MAX_START_OFFSET_DAYS = 90;
    private static final long MAX_FORECAST_DAYS = 90;
    private static final long DEFAULT_FORECAST_DAYS = 90;
    private static final long MAX_END_ONLY_OFFSET_DAYS =
            MAX_START_OFFSET_DAYS + MAX_FORECAST_DAYS - 1;

    /**
     * 시작일·종료일 입력 조합에 날짜 기본값과 최대 기간 정책 적용
     */
    public ForecastDateRange resolve(
            LocalDate preferredStartDate,
            LocalDate preferredEndDate,
            LocalDate today
    ) {
        if (today == null) {
            throw new IllegalArgumentException("today must not be null");
        }
        if (preferredStartDate != null
                && preferredEndDate != null
                && preferredStartDate.isAfter(preferredEndDate)) {
            throw new AppException(ErrorCode.AI_STRATEGY_START_AFTER_END);
        }

        validateStartDate(preferredStartDate, today);

        if (preferredStartDate == null && preferredEndDate == null) {
            return new ForecastDateRange(today, today.plusDays(DEFAULT_FORECAST_DAYS - 1));
        }
        if (preferredStartDate != null && preferredEndDate == null) {
            return new ForecastDateRange(
                    preferredStartDate,
                    preferredStartDate.plusDays(MAX_FORECAST_DAYS - 1)
            );
        }
        if (preferredStartDate == null) {
            validateEndOnlyDate(preferredEndDate, today);
            LocalDate rangeStart = preferredEndDate.minusDays(MAX_FORECAST_DAYS - 1);
            // 종료일만 지정해도 이미 지난 날짜의 예측을 요청하지 않도록 오늘로 보정
            return new ForecastDateRange(rangeStart.isBefore(today) ? today : rangeStart, preferredEndDate);
        }
        if (preferredEndDate.isAfter(preferredStartDate.plusDays(MAX_FORECAST_DAYS - 1))) {
            throw new AppException(ErrorCode.AI_STRATEGY_DATE_OUT_OF_RANGE);
        }
        return new ForecastDateRange(preferredStartDate, preferredEndDate);
    }

    private static void validateStartDate(LocalDate startDate, LocalDate today) {
        if (startDate != null
                && (startDate.isBefore(today)
                || startDate.isAfter(today.plusDays(MAX_START_OFFSET_DAYS)))) {
            throw new AppException(ErrorCode.AI_STRATEGY_DATE_OUT_OF_RANGE);
        }
    }

    private static void validateEndOnlyDate(LocalDate endDate, LocalDate today) {
        if (endDate.isBefore(today)
                || endDate.isAfter(today.plusDays(MAX_END_ONLY_OFFSET_DAYS))) {
            throw new AppException(ErrorCode.AI_STRATEGY_DATE_OUT_OF_RANGE);
        }
    }
}
