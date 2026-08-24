package com.stockit.backend.feature.strategy.calculation.candidate.policy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext.InventoryLot;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext.SalesPoint;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationException;
import com.stockit.backend.feature.strategy.calculation.engine.CalculationPrecisionPolicy;

/**
 * 전략 시작 시점의 예상 재고와 안전재고를 반영해 실행 가능한 창고별 수량을 계산하는 정책
 */
@Component
public class SourceInventoryCapacityPolicy {

    private final SafetyStockPolicyResolver safetyStockResolver;

    public SourceInventoryCapacityPolicy(
            SafetyStockPolicyResolver safetyStockResolver
    ) {
        this.safetyStockResolver = safetyStockResolver;
    }

    /** 현재 전략 시작일을 기준으로 선택 LOT의 실행 가능량을 계산한다 */
    public Capacity resolve(
            StrategyCalculationContext context,
            List<InventoryLot> eligibleLots
    ) {
        return resolve(context, eligibleLots, context.strategyStartDate());
    }

    /**
     * 지정 시점의 판매 가능 재고에서 안전재고를 제외한 전략 실행 가능량을 계산한다
     */
    public Capacity resolve(
            StrategyCalculationContext context,
            List<InventoryLot> eligibleLots,
            LocalDate asOfDate
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

    /**
     * 전략 시작일 직전까지 기본 수요와 LOT 만료를 반영한 예상 재고 스냅샷을 생성한다
     *
     * <p>후보 계산용 재고와 안전재고 판단용 전체 재고를 각각 투영해
     * 선택 범위와 기준 범위가 섞이지 않도록 유지한다</p>
     */
    public Projection projectAt(
            StrategyCalculationContext context,
            LocalDate strategyStartDate
    ) {
        if (strategyStartDate == null
                || strategyStartDate.isBefore(context.calculatedAt().toLocalDate())) {
            throw new IllegalArgumentException("strategy start date is invalid");
        }

        if (context.sourceSalesPointId() != null
                && !context.salesPoints().containsKey(context.sourceSalesPointId())) {
            throw new StrategyCalculationException(
                    "CALCULATION_SOURCE_NOT_FOUND",
                    "Source sales point is missing from calculation context"
            );
        }
        return new Projection(
                projectInventory(
                        context,
                        context.evaluationInventory(),
                        strategyStartDate
                ),
                projectInventory(
                        context,
                        context.referenceInventory(),
                        strategyStartDate
                )
        );
    }

    private static List<InventoryLot> projectInventory(
            StrategyCalculationContext context,
            List<InventoryLot> inventory,
            LocalDate strategyStartDate
    ) {
        Map<Long, ProjectedLotState> statesByBalanceId = new LinkedHashMap<>();
        for (InventoryLot lot : inventory) {
            ProjectedLotState duplicate = statesByBalanceId.put(
                    lot.inventoryBalanceId(),
                    new ProjectedLotState(lot)
            );
            if (duplicate != null) {
                throw new StrategyCalculationException(
                        "CALCULATION_INVENTORY_DUPLICATED",
                        "Inventory balance is duplicated in calculation context"
                );
            }
        }
        Map<Long, List<ProjectedLotState>> statesBySalesPoint = statesByBalanceId
                .values().stream()
                .filter(state -> state.input.effectiveSalesPointId() != null)
                .filter(state -> context.salesPoints().containsKey(
                        state.input.effectiveSalesPointId()
                ))
                .collect(Collectors.groupingBy(
                        state -> state.input.effectiveSalesPointId(),
                        LinkedHashMap::new,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                values -> values.stream()
                                        .sorted(ProjectedLotState.OUTBOUND_ORDER)
                                        .toList()
                        )
                ));

        // 전략 시작 당일 판매는 전략 조건으로 처리하므로 시작일 직전까지만 정상 판매 반영
        for (LocalDate date = context.calculatedAt().toLocalDate();
                date.isBefore(strategyStartDate);
                date = date.plusDays(1)) {
            for (ProjectedLotState lot : statesByBalanceId.values()) {
                if (lot.isExpiredAt(date)) {
                    lot.remaining = BigDecimal.ZERO;
                }
            }
            for (Map.Entry<Long, List<ProjectedLotState>> entry
                    : statesBySalesPoint.entrySet()) {
                SalesPoint salesPoint = context.salesPoints().get(entry.getKey());
                BigDecimal forecast = salesPoint.dailyForecast().get(date);
                if (forecast == null || forecast.signum() < 0) {
                    throw new StrategyCalculationException(
                            "CALCULATION_FORECAST_INVALID",
                            "Daily forecast is missing or negative for sales point "
                                    + entry.getKey() + ": " + date
                    );
                }
                BigDecimal remainingDemand = quantity(forecast);
                for (ProjectedLotState lot : entry.getValue()) {
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
            }
        }

        return inventory.stream()
                .map(lot -> copyWithQuantity(
                        lot,
                        statesByBalanceId.get(lot.inventoryBalanceId()).remaining
                ))
                .toList();
    }

    private static InventoryLot copyWithQuantity(
            InventoryLot lot,
            BigDecimal availableQty
    ) {
        return new InventoryLot(
                lot.inventoryBalanceId(),
                lot.lotId(),
                lot.warehouseId(),
                lot.stockSalesPointId(),
                lot.allocatedSalesPointId(),
                quantity(availableQty),
                lot.reservedQty(),
                lot.manufacturedDate(),
                lot.receivedDate(),
                lot.expiryDate(),
                lot.saleStopDate(),
                lot.lotStatus()
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

    public record Projection(
            List<InventoryLot> evaluationInventory,
            List<InventoryLot> referenceInventory
    ) {
        public Projection {
            evaluationInventory = List.copyOf(evaluationInventory);
            referenceInventory = List.copyOf(referenceInventory);
        }
    }

    private static final class ProjectedLotState {

        // 소비기한 상품은 FEFO, 소비기한이 없는 공산품은 입고일 기준 FIFO 적용
        private static final Comparator<ProjectedLotState> OUTBOUND_ORDER = Comparator
                .comparing(ProjectedLotState::expirySortDate)
                .thenComparing(ProjectedLotState::receivedSortDate)
                .thenComparing(ProjectedLotState::manufacturedSortDate)
                .thenComparing(state -> state.input.inventoryBalanceId());

        private final InventoryLot input;
        private BigDecimal remaining;

        private ProjectedLotState(InventoryLot input) {
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
