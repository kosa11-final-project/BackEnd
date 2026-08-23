package com.stockit.backend.feature.strategy.calculation.candidate.policy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext.InventoryLot;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext.SalesPoint;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationException;
import com.stockit.backend.feature.strategy.calculation.engine.CalculationPrecisionPolicy;

/**
 * 대상 판매처의 기존 재고로 충족할 수 없는 일자별 추가 판매 가능 수요를 계산한다.
 *
 * <p>{@code availableQty}는 DB의 {@code on_hand_qty}로, 예약분을 제외한
 * 판매가능재고이므로 {@code reservedQty}를 다시 차감하지 않는다.</p>
 */
@Component
public class TargetAdditionalDemandPolicy {

    public Map<LocalDate, BigDecimal> calculate(
            StrategyCalculationContext context,
            Long targetSalesPointId
    ) {
        SalesPoint target = context.salesPoints().get(targetSalesPointId);
        if (target == null) {
            throw new StrategyCalculationException(
                    "CANDIDATE_TARGET_NOT_FOUND",
                    "Target sales point is missing from calculation context"
            );
        }
        List<LotState> existingLots = context.referenceInventory().stream()
                .filter(lot -> Objects.equals(
                        lot.effectiveSalesPointId(),
                        targetSalesPointId
                ))
                .filter(lot -> "AVAILABLE".equals(lot.lotStatus()))
                .map(LotState::new)
                .sorted(LotState.OUTBOUND_ORDER)
                .toList();
        Map<LocalDate, BigDecimal> unmetByDate = new LinkedHashMap<>();

        for (LocalDate date = context.strategyStartDate();
                !date.isAfter(context.strategyEndDate());
                date = date.plusDays(1)) {
            BigDecimal forecast = target.dailyForecast().get(date);
            if (forecast == null || forecast.signum() < 0) {
                throw new StrategyCalculationException(
                        "CALCULATION_FORECAST_INVALID",
                        "Target daily forecast is missing or negative: " + date
                );
            }
            BigDecimal remainingDemand = quantity(forecast);
            for (LotState lot : existingLots) {
                if (remainingDemand.signum() == 0) {
                    break;
                }
                if (!lot.isSellableAt(date) || lot.remaining.signum() == 0) {
                    continue;
                }
                BigDecimal sold = lot.remaining.min(remainingDemand);
                lot.remaining = quantity(lot.remaining.subtract(sold));
                remainingDemand = quantity(remainingDemand.subtract(sold));
            }
            unmetByDate.put(date, remainingDemand);
        }
        return unmetByDate;
    }

    private static BigDecimal quantity(BigDecimal value) {
        return CalculationPrecisionPolicy.quantity(value);
    }

    private static final class LotState {

        private static final Comparator<LotState> OUTBOUND_ORDER = Comparator
                .comparing(LotState::expirySortDate)
                .thenComparing(LotState::receivedSortDate)
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

        private boolean isSellableAt(LocalDate date) {
            return (input.expiryDate() == null || !date.isAfter(input.expiryDate()))
                    && (input.saleStopDate() == null || date.isBefore(input.saleStopDate()));
        }
    }
}
