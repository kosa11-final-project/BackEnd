package com.stockit.backend.feature.strategy.service;

import java.time.LocalDate;

import org.springframework.stereotype.Component;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.strategy.domain.ForecastDateRange;

/**
 * 사용자 희망 기간을 검증하고 재고 투영에 필요한 실제 수요예측 범위를 결정하는 컴포넌트
 */
@Component
public class StrategyForecastDateRangeResolver {

    private static final long MAX_FORECAST_DAYS = 90;

    /**
     * 희망 시작일·종료일 조합에 기본값과 최대 90일 정책을 적용한다
     *
     * <p>미래 전략은 시작일까지 정상 판매된 재고를 먼저 계산해야 하므로,
     * 실제 예측 범위는 희망 전략 기간보다 앞선 오늘부터 시작할 수 있다</p>
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
        validateEndDate(preferredEndDate, today);
        LocalDate maxForecastEndDate = today.plusDays(MAX_FORECAST_DAYS - 1);

        if (preferredStartDate == null && preferredEndDate == null) {
            return new ForecastDateRange(today, maxForecastEndDate);
        }
        if (preferredStartDate != null && preferredEndDate == null) {
            // 전략 시작 전 정상 판매량까지 반영하기 위한 오늘 기준 예측
            return new ForecastDateRange(today, maxForecastEndDate);
        }
        if (preferredStartDate == null) {
            return new ForecastDateRange(today, preferredEndDate);
        }
        if (preferredEndDate.isAfter(preferredStartDate.plusDays(MAX_FORECAST_DAYS - 1))) {
            throw new AppException(ErrorCode.AI_STRATEGY_DATE_OUT_OF_RANGE);
        }
        // 희망 기간 전 판매량을 재고 투영에 포함하기 위한 예측 시작일 분리
        return new ForecastDateRange(today, preferredEndDate);
    }

    private static void validateStartDate(LocalDate startDate, LocalDate today) {
        if (startDate != null
                && (startDate.isBefore(today)
                || startDate.isAfter(today.plusDays(MAX_FORECAST_DAYS - 1)))) {
            throw new AppException(ErrorCode.AI_STRATEGY_DATE_OUT_OF_RANGE);
        }
    }

    private static void validateEndDate(LocalDate endDate, LocalDate today) {
        if (endDate != null
                && (endDate.isBefore(today)
                || endDate.isAfter(today.plusDays(MAX_FORECAST_DAYS - 1)))) {
            throw new AppException(ErrorCode.AI_STRATEGY_DATE_OUT_OF_RANGE);
        }
    }
}
