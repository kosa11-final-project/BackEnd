package com.stockit.backend.feature.strategy.calculation.candidate.policy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.stockit.backend.feature.strategy.calculation.candidate.domain.MovementCandidatePlan;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext.InventoryLot;
import com.stockit.backend.feature.strategy.calculation.engine.CalculationPrecisionPolicy;

/** FEFO(소비기한 없음은 FIFO)로 LOT를 대상 판매처의 일자별 미충족 수요에 배분한다. */
@Component
public class MovementLotAllocationPolicy {

    public MovementCandidatePlan plan(
            List<InventoryLot> eligibleLots,
            Map<Long, BigDecimal> sourceCapacityByWarehouse,
            Map<LocalDate, BigDecimal> targetUnmetDemand,
            BigDecimal requestedQuantity
    ) {
        BigDecimal remainingRequest = quantity(requestedQuantity);
        Map<Long, BigDecimal> remainingSourceCapacity = new LinkedHashMap<>();
        sourceCapacityByWarehouse.forEach((warehouseId, capacity) ->
                remainingSourceCapacity.put(warehouseId, quantity(capacity)));
        Map<LocalDate, BigDecimal> remainingDemand = new LinkedHashMap<>();
        targetUnmetDemand.forEach((date, demand) ->
                remainingDemand.put(date, quantity(demand)));

        List<InventoryLot> orderedLots = eligibleLots.stream()
                .filter(lot -> "AVAILABLE".equals(lot.lotStatus()))
                .sorted(OUTBOUND_ORDER)
                .toList();
        List<MovementCandidatePlan.Allocation> allocations = new ArrayList<>();

        for (InventoryLot lot : orderedLots) {
            if (remainingRequest.signum() == 0) {
                break;
            }
            BigDecimal warehouseCapacity = remainingSourceCapacity.getOrDefault(
                    lot.warehouseId(),
                    BigDecimal.ZERO
            );
            BigDecimal lotLimit = quantity(lot.availableQty()
                    .min(warehouseCapacity)
                    .min(remainingRequest));
            if (lotLimit.signum() == 0) {
                continue;
            }

            BigDecimal allocated = BigDecimal.ZERO;
            for (Map.Entry<LocalDate, BigDecimal> entry : remainingDemand.entrySet()) {
                if (allocated.compareTo(lotLimit) >= 0) {
                    break;
                }
                if (!isSellableAt(lot, entry.getKey()) || entry.getValue().signum() == 0) {
                    continue;
                }
                BigDecimal supplied = quantity(entry.getValue().min(
                        lotLimit.subtract(allocated)
                ));
                entry.setValue(quantity(entry.getValue().subtract(supplied)));
                allocated = quantity(allocated.add(supplied));
            }
            if (allocated.signum() == 0) {
                continue;
            }
            remainingSourceCapacity.put(
                    lot.warehouseId(),
                    quantity(warehouseCapacity.subtract(allocated))
            );
            remainingRequest = quantity(remainingRequest.subtract(allocated));
            allocations.add(new MovementCandidatePlan.Allocation(
                    lot.inventoryBalanceId(),
                    lot.lotId(),
                    lot.warehouseId(),
                    lot.effectiveSalesPointId(),
                    allocated
            ));
        }
        BigDecimal total = quantity(allocations.stream()
                .map(MovementCandidatePlan.Allocation::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        return new MovementCandidatePlan(total, allocations);
    }

    private static final Comparator<InventoryLot> OUTBOUND_ORDER = Comparator
            .comparing(MovementLotAllocationPolicy::expirySortDate)
            .thenComparing(MovementLotAllocationPolicy::receivedSortDate)
            .thenComparing(MovementLotAllocationPolicy::manufacturedSortDate)
            .thenComparing(InventoryLot::inventoryBalanceId);

    private static LocalDate expirySortDate(InventoryLot lot) {
        return lot.expiryDate() == null ? LocalDate.MAX : lot.expiryDate();
    }

    private static LocalDate receivedSortDate(InventoryLot lot) {
        return lot.receivedDate() == null ? LocalDate.MAX : lot.receivedDate();
    }

    private static LocalDate manufacturedSortDate(InventoryLot lot) {
        return lot.manufacturedDate() == null ? LocalDate.MAX : lot.manufacturedDate();
    }

    private static boolean isSellableAt(InventoryLot lot, LocalDate date) {
        return (lot.expiryDate() == null || !date.isAfter(lot.expiryDate()))
                && (lot.saleStopDate() == null || date.isBefore(lot.saleStopDate()));
    }

    private static BigDecimal quantity(BigDecimal value) {
        return CalculationPrecisionPolicy.quantity(value);
    }
}
