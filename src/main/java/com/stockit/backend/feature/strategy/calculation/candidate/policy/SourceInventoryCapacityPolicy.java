package com.stockit.backend.feature.strategy.calculation.candidate.policy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext.InventoryLot;
import com.stockit.backend.feature.strategy.calculation.engine.CalculationPrecisionPolicy;

/** 선택 LOT 중 안전재고를 남기고 전략에 사용할 수 있는 창고별 수량을 계산한다. */
@Component
public class SourceInventoryCapacityPolicy {

    private final SafetyStockPolicyResolver safetyStockResolver;

    public SourceInventoryCapacityPolicy(
            SafetyStockPolicyResolver safetyStockResolver
    ) {
        this.safetyStockResolver = safetyStockResolver;
    }

    public Capacity resolve(
            StrategyCalculationContext context,
            List<InventoryLot> eligibleLots
    ) {
        Map<Long, List<InventoryLot>> selectedByWarehouse = eligibleLots.stream()
                .filter(lot -> lot.warehouseId() != null)
                .collect(Collectors.groupingBy(
                        InventoryLot::warehouseId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        Map<Long, BigDecimal> byWarehouse = new LinkedHashMap<>();
        boolean defaulted = false;
        boolean safetyBlocked = false;
        LocalDate asOfDate = context.calculatedAt().toLocalDate();

        for (Map.Entry<Long, List<InventoryLot>> entry : selectedByWarehouse.entrySet()) {
            Long warehouseId = entry.getKey();
            BigDecimal selectedQuantity = sum(entry.getValue().stream()
                    .map(InventoryLot::availableQty)
                    .toList());
            BigDecimal sourceAvailable = sum(context.referenceInventory().stream()
                    .filter(lot -> Objects.equals(lot.warehouseId(), warehouseId))
                    .filter(lot -> matchesSource(lot, context.sourceSalesPointId()))
                    .filter(lot -> isSellableAt(lot, asOfDate))
                    .map(InventoryLot::availableQty)
                    .toList());
            SafetyStockPolicyResolver.Resolution safety = safetyStockResolver.resolve(
                    context.inventoryPolicies(),
                    warehouseId,
                    context.sourceSalesPointId()
            );
            defaulted |= safety.defaultedToZero();
            BigDecimal afterSafety = quantity(sourceAvailable.subtract(
                    safety.safetyStockQty()
            ).max(BigDecimal.ZERO));
            safetyBlocked |= sourceAvailable.signum() > 0 && afterSafety.signum() == 0;
            byWarehouse.put(warehouseId, quantity(selectedQuantity.min(afterSafety)));
        }
        return new Capacity(
                Map.copyOf(byWarehouse),
                sum(byWarehouse.values()),
                defaulted,
                safetyBlocked
        );
    }

    private static boolean matchesSource(InventoryLot lot, Long sourceSalesPointId) {
        return sourceSalesPointId == null
                ? lot.isPublicUnassigned()
                : Objects.equals(lot.effectiveSalesPointId(), sourceSalesPointId);
    }

    private static boolean isSellableAt(InventoryLot lot, LocalDate date) {
        return "AVAILABLE".equals(lot.lotStatus())
                && (lot.expiryDate() == null || !date.isAfter(lot.expiryDate()))
                && (lot.saleStopDate() == null || date.isBefore(lot.saleStopDate()));
    }

    private static BigDecimal sum(Iterable<BigDecimal> values) {
        BigDecimal result = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            result = result.add(value);
        }
        return quantity(result);
    }

    private static BigDecimal quantity(BigDecimal value) {
        return CalculationPrecisionPolicy.quantity(value);
    }

    public record Capacity(
            Map<Long, BigDecimal> byWarehouse,
            BigDecimal total,
            boolean safetyStockDefaulted,
            boolean safetyStockBlocked
    ) {
    }
}
