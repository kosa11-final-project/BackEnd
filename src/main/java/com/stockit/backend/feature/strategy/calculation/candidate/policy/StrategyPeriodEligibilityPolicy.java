package com.stockit.backend.feature.strategy.calculation.candidate.policy;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext.InventoryLot;

/**
 * 전략 실행 기간의 현재성, 수요예측 범위와 배정 LOT의 판매 가능 종료일을 계산한다.
 */
@Component
public class StrategyPeriodEligibilityPolicy {

    public static final int MAXIMUM_PERIOD_DAYS = 90;

    /** 오늘과 수요예측 시작일 중 늦은 날짜를 현재 선택 가능한 시작일로 반환한다. */
    public LocalDate minimumStartDate(
            StrategyCalculationContext context,
            LocalDate businessDate
    ) {
        if (context == null || businessDate == null) {
            throw new IllegalArgumentException("period policy input must not be null");
        }
        return businessDate.isAfter(context.forecastStartDate())
                ? businessDate
                : context.forecastStartDate();
    }

    /**
     * 실제 평가 또는 배정된 LOT 중 마지막까지 판매 가능한 날짜를 반환한다.
     *
     * <p>여러 LOT 중 앞 LOT가 만료돼도 다음 LOT를 계속 판매할 수 있으므로
     * 가장 이른 날짜가 아니라 가장 늦은 판매 가능 종료일을 사용한다.</p>
     */
    public LocalDate latestSelectableEndDate(
            StrategyCalculationContext context,
            Collection<Long> allocatedInventoryBalanceIds
    ) {
        if (context == null || allocatedInventoryBalanceIds == null) {
            throw new IllegalArgumentException("period policy input must not be null");
        }
        Set<Long> allocatedIds = new HashSet<>(allocatedInventoryBalanceIds);
        List<InventoryLot> lots = context.evaluationInventory().stream()
                .filter(lot -> allocatedIds.isEmpty()
                        || allocatedIds.contains(lot.inventoryBalanceId()))
                .filter(lot -> lot.availableQty().signum() > 0)
                .toList();
        if (lots.isEmpty()) {
            throw new AppException(
                    ErrorCode.AI_STRATEGY_SIMULATION_INVALID,
                    "전략 적용 수량에 대응하는 재고 LOT를 찾을 수 없습니다."
            );
        }

        LocalDate latest = null;
        for (InventoryLot lot : lots) {
            LocalDate sellableEnd = sellableEndDate(lot);
            if (sellableEnd == null) {
                return context.forecastEndDate();
            }
            if (latest == null || sellableEnd.isAfter(latest)) {
                latest = sellableEnd;
            }
        }
        return latest.isAfter(context.forecastEndDate())
                ? context.forecastEndDate()
                : latest;
    }

    /** 소비기한 당일과 판매중지일 전날 중 더 빠른 날짜를 반환한다. */
    public LocalDate sellableEndDate(InventoryLot lot) {
        if (lot == null) {
            throw new IllegalArgumentException("inventory lot must not be null");
        }
        LocalDate expiry = lot.expiryDate();
        LocalDate saleStop = lot.saleStopDate() == null
                ? null
                : lot.saleStopDate().minusDays(1);
        if (expiry == null) return saleStop;
        if (saleStop == null) return expiry;
        return expiry.isBefore(saleStop) ? expiry : saleStop;
    }

    /** 현재 날짜와 전체 평가 LOT 기준으로 조정 요청의 1차 기간을 검증한다. */
    public void validateRequestedPeriod(
            StrategyCalculationContext context,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate businessDate
    ) {
        validateCommonRange(context, startDate, endDate);
        LocalDate minimumStart = minimumStartDate(context, businessDate);
        if (startDate.isBefore(minimumStart)) {
            throw new AppException(
                    ErrorCode.AI_STRATEGY_PERIOD_STALE,
                    "전략 시작일은 " + minimumStart + " 이후여야 합니다."
            );
        }
        validateSellableEnd(
                context,
                endDate,
                latestSelectableEndDate(context, List.of())
        );
    }

    /** 실제 재배정된 LOT 기준으로 종료일을 다시 검증한다. */
    public void validateAllocatedPeriod(
            StrategyCalculationContext context,
            LocalDate endDate,
            Collection<Long> allocatedInventoryBalanceIds
    ) {
        validateSellableEnd(
                context,
                endDate,
                latestSelectableEndDate(context, allocatedInventoryBalanceIds)
        );
    }

    /** 상세·조정 응답에 제공할 현재 실행 가능 범위를 계산한다. */
    public PeriodConstraints constraints(
            StrategyCalculationContext context,
            LocalDate startDate,
            LocalDate endDate,
            Collection<Long> allocatedInventoryBalanceIds,
            LocalDate businessDate
    ) {
        LocalDate minimumStart = minimumStartDate(context, businessDate);
        LocalDate latestEnd = latestSelectableEndDate(
                context,
                allocatedInventoryBalanceIds
        );
        boolean requiresAdjustment = startDate == null
                || endDate == null
                || startDate.isBefore(minimumStart)
                || endDate.isAfter(latestEnd)
                || startDate.isAfter(endDate)
                || inclusiveDays(startDate, endDate) > MAXIMUM_PERIOD_DAYS;
        return new PeriodConstraints(
                minimumStart,
                latestEnd,
                MAXIMUM_PERIOD_DAYS,
                requiresAdjustment
        );
    }

    private static void validateCommonRange(
            StrategyCalculationContext context,
            LocalDate startDate,
            LocalDate endDate
    ) {
        if (context == null || startDate == null || endDate == null
                || startDate.isAfter(endDate)
                || startDate.isBefore(context.forecastStartDate())
                || endDate.isAfter(context.forecastEndDate())
                || inclusiveDays(startDate, endDate) > MAXIMUM_PERIOD_DAYS) {
            throw new AppException(ErrorCode.AI_STRATEGY_DATE_OUT_OF_RANGE);
        }
    }

    private static void validateSellableEnd(
            StrategyCalculationContext context,
            LocalDate requestedEndDate,
            LocalDate latestSelectableEndDate
    ) {
        if (latestSelectableEndDate.isBefore(context.forecastStartDate())
                || requestedEndDate.isAfter(latestSelectableEndDate)) {
            throw new AppException(
                    ErrorCode.AI_STRATEGY_SELLABLE_END_EXCEEDED,
                    "전략 종료일은 " + latestSelectableEndDate + " 이전이어야 합니다."
            );
        }
    }

    private static long inclusiveDays(LocalDate startDate, LocalDate endDate) {
        return ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }

    public record PeriodConstraints(
            LocalDate minimumStartDate,
            LocalDate latestSelectableEndDate,
            int maximumPeriodDays,
            boolean requiresPeriodAdjustment
    ) {
    }
}
