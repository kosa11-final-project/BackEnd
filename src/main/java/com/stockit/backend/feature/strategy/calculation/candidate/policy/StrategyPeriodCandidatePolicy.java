package com.stockit.backend.feature.strategy.calculation.candidate.policy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.stockit.backend.feature.strategy.calculation.candidate.domain.StrategyPeriodCandidate;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext.InventoryLot;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext.SalesPoint;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationException;

/**
 * 사용자 고정일과 90일 이내 수요·소비기한 경계를 반영해 기간 후보를 제한하는 정책
 */
@Component
public class StrategyPeriodCandidatePolicy {

    private static final List<Integer> STANDARD_DAYS = List.of(7, 14, 30);
    private final StrategyPeriodEligibilityPolicy eligibilityPolicy;

    public StrategyPeriodCandidatePolicy(
            StrategyPeriodEligibilityPolicy eligibilityPolicy
    ) {
        this.eligibilityPolicy = eligibilityPolicy;
    }

    /** 사용자 고정 조건을 우선한 뒤 대표 기간과 고수요 기간 후보를 생성한다 */
    public List<StrategyPeriodCandidate> generate(
            StrategyCalculationContext context,
            Long salesPointId
    ) {
        LocalDate rangeStart = context.forecastStartDate();
        LocalDate rangeEnd = eligibilityPolicy.latestSelectableEndDate(
                context,
                List.of()
        );
        boolean startFixed = context.requestConstraints().isStartDateFixed();
        boolean endFixed = context.requestConstraints().isEndDateFixed();
        Set<StrategyPeriodCandidate> periods = new LinkedHashSet<>();

        if (rangeEnd.isBefore(rangeStart)) {
            return List.of();
        }

        // 사용자가 고정한 날짜도 수요예측과 전체 평가 LOT의 판매 가능 범위 안에서만 사용
        if (startFixed && endFixed) {
            LocalDate preferredStart = context.requestConstraints().preferredStartDate();
            LocalDate preferredEnd = context.requestConstraints().preferredEndDate();
            if (!isInsideRange(preferredStart, preferredEnd, rangeStart, rangeEnd)) {
                return List.of();
            }
            return List.of(new StrategyPeriodCandidate(
                    preferredStart,
                    preferredEnd
            ));
        }
        if (startFixed) {
            LocalDate preferredStart = context.requestConstraints().preferredStartDate();
            if (preferredStart.isBefore(rangeStart) || preferredStart.isAfter(rangeEnd)) {
                return List.of();
            }
            addForwardWindows(periods, preferredStart, rangeEnd);
            periods.add(new StrategyPeriodCandidate(preferredStart, rangeEnd));
            return List.copyOf(periods);
        }
        if (endFixed) {
            LocalDate preferredEnd = context.requestConstraints().preferredEndDate();
            if (preferredEnd.isBefore(rangeStart) || preferredEnd.isAfter(rangeEnd)) {
                return List.of();
            }
            addBackwardWindows(periods, rangeStart, preferredEnd);
            periods.add(new StrategyPeriodCandidate(rangeStart, preferredEnd));
            return sorted(periods);
        }

        SalesPoint salesPoint = context.salesPoints().get(salesPointId);
        if (salesPoint == null) {
            throw new StrategyCalculationException(
                    "CANDIDATE_TARGET_NOT_FOUND",
                    "Sales point is missing from calculation context: " + salesPointId
            );
        }
        Map<LocalDate, BigDecimal> forecast = salesPoint.dailyForecast();
        LocalDate urgentEnd = earliestSellableEnd(context.evaluationInventory(), rangeEnd);
        for (Integer days : STANDARD_DAYS) {
            if (rangeStart.plusDays(days - 1L).isAfter(rangeEnd)) {
                continue;
            }
            periods.add(new StrategyPeriodCandidate(
                    rangeStart,
                    rangeStart.plusDays(days - 1L)
            ));
            periods.add(highestDemandWindow(forecast, rangeStart, rangeEnd, days));
            LocalDate urgentStart = urgentEnd.minusDays(days - 1L);
            if (!urgentStart.isBefore(rangeStart)) {
                periods.add(new StrategyPeriodCandidate(urgentStart, urgentEnd));
            }
        }
        periods.add(new StrategyPeriodCandidate(rangeStart, rangeEnd));
        return sorted(periods);
    }

    private static void addForwardWindows(
            Set<StrategyPeriodCandidate> periods,
            LocalDate start,
            LocalDate rangeEnd
    ) {
        for (Integer days : STANDARD_DAYS) {
            LocalDate end = start.plusDays(days - 1L);
            if (!end.isAfter(rangeEnd)) {
                periods.add(new StrategyPeriodCandidate(start, end));
            }
        }
    }

    private static void addBackwardWindows(
            Set<StrategyPeriodCandidate> periods,
            LocalDate rangeStart,
            LocalDate end
    ) {
        for (Integer days : STANDARD_DAYS) {
            LocalDate start = end.minusDays(days - 1L);
            if (!start.isBefore(rangeStart)) {
                periods.add(new StrategyPeriodCandidate(start, end));
            }
        }
    }

    private static StrategyPeriodCandidate highestDemandWindow(
            Map<LocalDate, BigDecimal> forecast,
            LocalDate rangeStart,
            LocalDate rangeEnd,
            int days
    ) {
        LocalDate bestStart = null;
        BigDecimal bestDemand = null;
        for (LocalDate start = rangeStart;
                !start.plusDays(days - 1L).isAfter(rangeEnd);
                start = start.plusDays(1)) {
            BigDecimal demand = BigDecimal.ZERO;
            for (int offset = 0; offset < days; offset++) {
                LocalDate date = start.plusDays(offset);
                BigDecimal daily = forecast.get(date);
                if (daily == null || daily.signum() < 0) {
                    throw new StrategyCalculationException(
                            "CALCULATION_FORECAST_INVALID",
                            "Daily forecast is missing or negative: " + date
                    );
                }
                demand = demand.add(daily);
            }
            if (bestDemand == null || demand.compareTo(bestDemand) > 0) {
                bestDemand = demand;
                bestStart = start;
            }
        }
        return new StrategyPeriodCandidate(bestStart, bestStart.plusDays(days - 1L));
    }

    private static LocalDate earliestSellableEnd(
            List<InventoryLot> lots,
            LocalDate rangeEnd
    ) {
        return lots.stream()
                .map(lot -> {
                    LocalDate expiry = lot.expiryDate();
                    LocalDate saleStop = lot.saleStopDate() == null
                            ? null
                            : lot.saleStopDate().minusDays(1);
                    if (expiry == null) {
                        return saleStop;
                    }
                    if (saleStop == null) {
                        return expiry;
                    }
                    return expiry.isBefore(saleStop) ? expiry : saleStop;
                })
                .filter(date -> date != null)
                .filter(date -> !date.isAfter(rangeEnd))
                .min(Comparator.naturalOrder())
                .orElse(rangeEnd);
    }

    private static List<StrategyPeriodCandidate> sorted(
            Set<StrategyPeriodCandidate> periods
    ) {
        List<StrategyPeriodCandidate> sorted = new ArrayList<>(periods);
        sorted.sort(Comparator
                .comparing(StrategyPeriodCandidate::startDate)
                .thenComparing(StrategyPeriodCandidate::endDate));
        return List.copyOf(sorted);
    }

    private static boolean isInsideRange(
            LocalDate start,
            LocalDate end,
            LocalDate rangeStart,
            LocalDate rangeEnd
    ) {
        return start != null && end != null
                && !start.isAfter(end)
                && !start.isBefore(rangeStart)
                && !end.isAfter(rangeEnd);
    }
}
