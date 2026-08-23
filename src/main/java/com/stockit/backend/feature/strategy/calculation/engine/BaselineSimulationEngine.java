package com.stockit.backend.feature.strategy.calculation.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.stockit.backend.feature.strategy.calculation.domain.BaselineSimulation;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext.InventoryLot;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext.Price;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext.SalesPoint;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationException;

/**
 * 할인, 이동, 채널 변경과 추가 입고가 없는 현재 상태의 일별 재고 흐름 계산기.
 */
@Component
public class BaselineSimulationEngine {

    private static final BigDecimal ZERO_QUANTITY = BigDecimal.ZERO.setScale(
            CalculationPrecisionPolicy.QUANTITY_SCALE
    );
    private static final BigDecimal ZERO_MONEY = BigDecimal.ZERO.setScale(
            CalculationPrecisionPolicy.MONEY_SCALE
    );

    /** 동일 Context에는 항상 동일한 결과를 반환하며 외부 상태를 변경하지 않는다. */
    public BaselineSimulation simulate(StrategyCalculationContext context) {
        SalesPoint source = resolveSource(context);
        Price sourcePrice = source == null ? null : source.price();
        BigDecimal unitContributionMargin = sourcePrice == null
                ? ZERO_MONEY
                : sourcePrice.actualPrice()
                        .subtract(context.unitCost())
                        .subtract(sourcePrice.paymentFee())
                        .subtract(sourcePrice.logisticsCost());

        List<LotState> lots = context.evaluationInventory().stream()
                .map(LotState::new)
                .sorted(LotState.OUTBOUND_ORDER)
                .toList();
        BigDecimal cumulativeSales = ZERO_QUANTITY;
        BigDecimal cumulativeRevenue = ZERO_MONEY;
        BigDecimal cumulativeContributionMargin = ZERO_MONEY;
        BigDecimal cumulativeDisposal = ZERO_QUANTITY;
        Integer sellThroughDays = null;
        List<BaselineSimulation.DailyPoint> dailySeries = new ArrayList<>();

        for (LocalDate date = context.forecastStartDate();
                !date.isAfter(context.forecastEndDate());
                date = date.plusDays(1)) {
            BigDecimal disposedToday = disposeExpired(lots, date);
            cumulativeDisposal = quantity(cumulativeDisposal.add(disposedToday));

            BigDecimal demand = source == null
                    ? ZERO_QUANTITY
                    : quantity(requiredForecast(source, date));
            BigDecimal soldToday = consumeSellableLots(lots, date, demand);
            BigDecimal revenueToday = sourcePrice == null
                    ? ZERO_MONEY
                    : money(soldToday.multiply(sourcePrice.actualPrice()));
            BigDecimal contributionToday = sourcePrice == null
                    ? ZERO_MONEY
                    : money(soldToday.multiply(unitContributionMargin));

            cumulativeSales = quantity(cumulativeSales.add(soldToday));
            cumulativeRevenue = money(cumulativeRevenue.add(revenueToday));
            cumulativeContributionMargin = money(
                    cumulativeContributionMargin.add(contributionToday)
            );
            BigDecimal remaining = totalRemaining(lots);
            if (sellThroughDays == null
                    && remaining.signum() == 0
                    && cumulativeDisposal.signum() == 0) {
                sellThroughDays = Math.toIntExact(
                        ChronoUnit.DAYS.between(context.forecastStartDate(), date) + 1
                );
            }

            dailySeries.add(new BaselineSimulation.DailyPoint(
                    date,
                    soldToday,
                    remaining,
                    cumulativeRevenue,
                    cumulativeContributionMargin
            ));
        }

        BigDecimal contributionMarginRate = cumulativeRevenue.signum() == 0
                ? BigDecimal.ZERO.setScale(CalculationPrecisionPolicy.RATE_SCALE)
                : CalculationPrecisionPolicy.rate(
                        cumulativeContributionMargin.divide(
                                cumulativeRevenue,
                                CalculationPrecisionPolicy.RATE_SCALE,
                                RoundingMode.HALF_UP
                        )
                );
        BaselineSimulation.Summary summary = new BaselineSimulation.Summary(
                cumulativeSales,
                cumulativeRevenue,
                cumulativeContributionMargin,
                contributionMarginRate,
                sellThroughDays,
                totalRemaining(lots),
                cumulativeDisposal
        );
        return new BaselineSimulation(summary, dailySeries);
    }

    private static SalesPoint resolveSource(StrategyCalculationContext context) {
        if (context.sourceSalesPointId() == null) {
            return null;
        }
        SalesPoint source = context.salesPoints().get(context.sourceSalesPointId());
        if (source == null) {
            throw new StrategyCalculationException(
                    "CALCULATION_SOURCE_NOT_FOUND",
                    "Source sales point is missing from calculation context"
            );
        }
        if (!source.hasCompletePrice()) {
            throw new StrategyCalculationException(
                    "CALCULATION_SOURCE_PRICE_INVALID",
                    "Source sales point price or variable cost is incomplete"
            );
        }
        return source;
    }

    private static BigDecimal requiredForecast(SalesPoint source, LocalDate date) {
        BigDecimal predicted = source.dailyForecast().get(date);
        if (predicted == null || predicted.signum() < 0) {
            throw new StrategyCalculationException(
                    "CALCULATION_FORECAST_INVALID",
                    "Source sales point daily forecast is missing or negative: " + date
            );
        }
        return predicted;
    }

    private static BigDecimal disposeExpired(List<LotState> lots, LocalDate date) {
        BigDecimal disposed = ZERO_QUANTITY;
        for (LotState lot : lots) {
            if (lot.isExpiredAt(date) && lot.remaining.signum() > 0) {
                disposed = disposed.add(lot.remaining);
                lot.remaining = ZERO_QUANTITY;
            }
        }
        return quantity(disposed);
    }

    private static BigDecimal consumeSellableLots(
            List<LotState> lots,
            LocalDate date,
            BigDecimal demand
    ) {
        BigDecimal remainingDemand = demand;
        BigDecimal sold = ZERO_QUANTITY;
        for (LotState lot : lots) {
            if (remainingDemand.signum() <= 0) {
                break;
            }
            if (!lot.isSellableAt(date) || lot.remaining.signum() <= 0) {
                continue;
            }
            BigDecimal consumed = lot.remaining.min(remainingDemand);
            lot.remaining = quantity(lot.remaining.subtract(consumed));
            remainingDemand = quantity(remainingDemand.subtract(consumed));
            sold = sold.add(consumed);
        }
        return quantity(sold);
    }

    private static BigDecimal totalRemaining(List<LotState> lots) {
        return quantity(lots.stream()
                .map(lot -> lot.remaining)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private static BigDecimal quantity(BigDecimal value) {
        return CalculationPrecisionPolicy.quantity(value);
    }

    private static BigDecimal money(BigDecimal value) {
        return CalculationPrecisionPolicy.money(value);
    }

    private static final class LotState {

        private static final Comparator<LotState> OUTBOUND_ORDER = Comparator
                .comparing(LotState::expirySortDate)
                .thenComparing(LotState::receivedSortDate)
                .thenComparing(LotState::manufacturedSortDate)
                .thenComparing(lot -> lot.input.inventoryBalanceId());

        private final InventoryLot input;
        private BigDecimal remaining;

        private LotState(InventoryLot input) {
            this.input = input;
            this.remaining = quantity(input.availableQty());
        }

        private LocalDate expirySortDate() {
            return input.expiryDate() == null ? LocalDate.MAX : input.expiryDate();
        }

        private LocalDate receivedSortDate() {
            return input.receivedDate() == null ? LocalDate.MAX : input.receivedDate();
        }

        private LocalDate manufacturedSortDate() {
            return input.manufacturedDate() == null
                    ? LocalDate.MAX
                    : input.manufacturedDate();
        }

        private boolean isExpiredAt(LocalDate date) {
            return "EXPIRED".equals(input.lotStatus())
                    || (input.expiryDate() != null && date.isAfter(input.expiryDate()));
        }

        private boolean isSellableAt(LocalDate date) {
            if (isExpiredAt(date)
                    || "SALE_STOPPED".equals(input.lotStatus())
                    || "DEPLETED".equals(input.lotStatus())) {
                return false;
            }
            return input.saleStopDate() == null || date.isBefore(input.saleStopDate());
        }
    }
}
