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
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext.PhysicalLocation;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext.SalesPoint;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationException;
import com.stockit.backend.feature.strategy.calculation.engine.CalculationPrecisionPolicy;

/** 전략 시작 시점의 판매 가능 재고를 기준으로 실행 가능한 위치별 수량을 계산하는 정책 */
@Component
public class SourceInventoryCapacityPolicy {

    /** 현재 전략 시작일을 기준으로 선택 LOT의 실행 가능량을 계산한다 */
    public Capacity resolve(
            StrategyCalculationContext context,
            List<InventoryLot> eligibleLots
    ) {
        return resolve(context, eligibleLots, context.strategyStartDate());
    }

    /** 지정 시점의 판매 가능 재고 안에서 전략 실행 가능량을 계산한다 */
    public Capacity resolve(
            StrategyCalculationContext context,
            List<InventoryLot> eligibleLots,
            LocalDate asOfDate
    ) {
        Map<PhysicalLocation, List<InventoryLot>> selectedByLocation = eligibleLots.stream()
                .filter(lot -> lot.warehouseId() != null
                        || lot.effectiveSalesPointId() != null)
                .collect(Collectors.groupingBy(
                        PhysicalLocation::of,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        Map<PhysicalLocation, BigDecimal> byLocation = new LinkedHashMap<>();
        for (Map.Entry<PhysicalLocation, List<InventoryLot>> entry
                : selectedByLocation.entrySet()) {
            PhysicalLocation location = entry.getKey();
            BigDecimal selectedQuantity = sum(entry.getValue().stream()
                    .map(InventoryLot::availableQty)
                    .toList());
            BigDecimal sourceAvailable = sum(context.referenceInventory().stream()
                    .filter(lot -> samePhysicalLocation(lot, location))
                    .filter(lot -> matchesSource(lot, context.sourceSalesPointId()))
                    .filter(lot -> isSellableAt(lot, asOfDate))
                    .map(InventoryLot::availableQty)
                    .toList());
            byLocation.put(
                    location,
                    quantity(selectedQuantity.min(sourceAvailable))
            );
        }
        return new Capacity(
                Map.copyOf(byLocation),
                sum(byLocation.values())
        );
    }

    /**
     * 전략 시작일 직전까지 기본 수요와 LOT 만료를 반영한 예상 재고 스냅샷을 생성한다
     *
     * <p>전체 기준 재고를 실제 출고 순서로 한 번 투영한 뒤 평가 대상 LOT만 추출한다.
     * 선택하지 않은 선행 LOT이 먼저 수요를 충족하는 흐름을 보존하기 위함이다.</p>
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
        List<InventoryLot> projectedReference = projectInventory(
                context,
                context.referenceInventory(),
                strategyStartDate
        );
        Map<Long, InventoryLot> referenceByBalanceId = projectedReference.stream()
                .collect(Collectors.toMap(
                        InventoryLot::inventoryBalanceId,
                        lot -> lot,
                        (first, second) -> first,
                        LinkedHashMap::new
                ));
        List<InventoryLot> projectedEvaluation = context.evaluationInventory().stream()
                .map(lot -> projectedEvaluationLot(lot, referenceByBalanceId))
                .toList();
        return new Projection(projectedEvaluation, projectedReference);
    }

    private static InventoryLot projectedEvaluationLot(
            InventoryLot evaluationLot,
            Map<Long, InventoryLot> referenceByBalanceId
    ) {
        InventoryLot projected = referenceByBalanceId.get(
                evaluationLot.inventoryBalanceId()
        );
        if (projected == null) {
            throw new StrategyCalculationException(
                    "CALCULATION_INVENTORY_SCOPE_INVALID",
                    "Evaluation inventory is missing from reference inventory"
            );
        }
        return projected;
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

    private static boolean samePhysicalLocation(
            InventoryLot lot,
            PhysicalLocation location
    ) {
        if (location.warehouseId() != null) {
            return Objects.equals(lot.warehouseId(), location.warehouseId());
        }
        return lot.warehouseId() == null
                && Objects.equals(lot.effectiveSalesPointId(), location.salesPointId());
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
            Map<PhysicalLocation, BigDecimal> byLocation,
            BigDecimal total
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
