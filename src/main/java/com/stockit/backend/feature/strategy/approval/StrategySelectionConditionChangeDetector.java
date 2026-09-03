package com.stockit.backend.feature.strategy.approval;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.calculation.mapper.StrategyCalculationInputMapper;
import com.stockit.backend.feature.strategy.calculation.vo.StrategyCalculationCostVO;
import com.stockit.backend.feature.strategy.calculation.vo.StrategyCalculationInventoryVO;
import com.stockit.backend.feature.strategy.calculation.vo.StrategyCalculationPriceVO;
import com.stockit.backend.feature.strategy.calculation.vo.StrategyCalculationSalesPointVO;
import com.stockit.backend.feature.strategy.calculation.vo.StrategyCalculationWarehouseRouteVO;
import com.stockit.backend.feature.strategy.domain.StrategyType;
import com.stockit.backend.feature.strategy.result.StrategyGenerationResult;

/** 생성 Snapshot과 최종 선택 시점의 DB 입력을 비교해 사용자 표시용 변경 내역을 만든다. */
@Component
public class StrategySelectionConditionChangeDetector {

    private final StrategyCalculationInputMapper inputMapper;

    public StrategySelectionConditionChangeDetector(
            StrategyCalculationInputMapper inputMapper
    ) {
        this.inputMapper = inputMapper;
    }

    public List<StrategyExecutionConditionChange> detect(
            ResolvedStrategySelection resolved,
            LocalDate businessDate
    ) {
        List<StrategyExecutionConditionChange> changes = new ArrayList<>();
        StrategyGenerationResult.Candidate candidate = resolved.option().candidate();
        StrategyCalculationContext context = resolved.calculationContext();
        Long skuId = context.sku().skuId();

        detectInventoryChanges(
                candidate,
                context,
                inputMapper.selectInventory(skuId),
                businessDate,
                resolved.evaluationEndDate(),
                changes
        );
        detectSalesPointAndRouteChanges(candidate, context, changes);
        detectCommercialChanges(resolved, businessDate, skuId, changes);
        return List.copyOf(changes);
    }

    private static void detectInventoryChanges(
            StrategyGenerationResult.Candidate candidate,
            StrategyCalculationContext context,
            List<StrategyCalculationInventoryVO> currentRows,
            LocalDate businessDate,
            LocalDate requestedEndDate,
            List<StrategyExecutionConditionChange> changes
    ) {
        Map<Long, StrategyCalculationInventoryVO> currentById = new LinkedHashMap<>();
        currentRows.forEach(row -> currentById.put(
                row.getInventoryBalanceId(), row
        ));
        Map<Long, StrategyCalculationContext.InventoryLot> snapshotById =
                snapshotInventory(context);
        BigDecimal totalRequested = BigDecimal.ZERO;
        BigDecimal totalExecutable = BigDecimal.ZERO;

        for (Map.Entry<Long, BigDecimal> required
                : requiredInventory(candidate).entrySet()) {
            Long inventoryBalanceId = required.getKey();
            BigDecimal requested = required.getValue();
            totalRequested = totalRequested.add(requested);
            StrategyCalculationInventoryVO current = currentById.get(
                    inventoryBalanceId
            );
            StrategyCalculationContext.InventoryLot snapshot = snapshotById.get(
                    inventoryBalanceId
            );

            if (current == null || current.getLotId() == null) {
                changes.add(change(
                        StrategyExecutionConditionChangeType.INVENTORY_UNAVAILABLE,
                        "inventoryBalanceId",
                        "배정 재고",
                        subject(snapshot),
                        snapshot == null ? null : snapshot.availableQty(),
                        BigDecimal.ZERO,
                        requested,
                        BigDecimal.ZERO,
                        "개",
                        inventoryLabel(snapshot, inventoryBalanceId)
                                + " 재고를 현재 조회할 수 없습니다."
                ));
                continue;
            }

            StrategyExecutionConditionSubject subject = subject(current);
            boolean sellable = isSellableAt(current, businessDate);
            if (!sellable) {
                changes.add(change(
                        StrategyExecutionConditionChangeType.LOT_NOT_SELLABLE,
                        "lotStatus",
                        "LOT 판매 가능 상태",
                        subject,
                        snapshot == null ? null : snapshot.lotStatus(),
                        sellableState(current, businessDate),
                        "AVAILABLE",
                        null,
                        null,
                        lotLabel(current) + "가 현재 판매 가능한 상태가 아닙니다."
                ));
            }

            LocalDate currentSellableEnd = sellableEndDate(current);
            if (currentSellableEnd != null
                    && requestedEndDate.isAfter(currentSellableEnd)) {
                changes.add(change(
                        StrategyExecutionConditionChangeType
                                .SELLABLE_END_DATE_CHANGED,
                        "endDate",
                        "판매 가능 종료일",
                        subject,
                        snapshot == null ? null : sellableEndDate(snapshot),
                        currentSellableEnd,
                        requestedEndDate,
                        currentSellableEnd,
                        null,
                        lotLabel(current) + "의 현재 판매 가능 종료일을 초과합니다."
                ));
            }

            BigDecimal available = available(current);
            if (available.compareTo(requested) < 0) {
                changes.add(change(
                        StrategyExecutionConditionChangeType
                                .AVAILABLE_QUANTITY_DECREASED,
                        "availableQuantity",
                        "LOT 가용재고",
                        subject,
                        snapshot == null ? null : snapshot.availableQty(),
                        available,
                        requested,
                        available,
                        "개",
                        lotLabel(current) + "의 가용재고가 요청 수량보다 "
                                + requested.subtract(available)
                                + "개 부족합니다."
                ));
            }

            boolean allocationMatches = allocationIdentityMatches(
                    candidate, current
            );
            if (!allocationMatches) {
                String expected = expectedAllocationLocation(
                        candidate, inventoryBalanceId
                );
                changes.add(change(
                        StrategyExecutionConditionChangeType
                                .INVENTORY_LOCATION_CHANGED,
                        "sourceLocation",
                        "LOT 또는 출발 위치",
                        subject,
                        expected,
                        locationValue(
                                current.getWarehouseId(),
                                current.effectiveSalesPointId(),
                                current.getLotId()
                        ),
                        expected,
                        null,
                        null,
                        lotLabel(current) + "의 LOT 또는 출발 위치가 변경되었습니다."
                ));
            }

            if (sellable && allocationMatches) {
                totalExecutable = totalExecutable.add(available.min(requested));
            }
        }

        if (totalExecutable.compareTo(totalRequested) < 0) {
            changes.add(change(
                    StrategyExecutionConditionChangeType
                            .AVAILABLE_QUANTITY_DECREASED,
                    "actionQuantity",
                    "전체 실행 가능 수량",
                    null,
                    totalRequested,
                    totalExecutable,
                    totalRequested,
                    totalExecutable,
                    "개",
                    "현재 실행 가능 수량이 요청 수량보다 "
                            + totalRequested.subtract(totalExecutable)
                            + "개 부족합니다."
            ));
        }
    }

    private void detectSalesPointAndRouteChanges(
            StrategyGenerationResult.Candidate candidate,
            StrategyCalculationContext context,
            List<StrategyExecutionConditionChange> changes
    ) {
        Set<Long> salesPointIds = new LinkedHashSet<>();
        candidate.actions().forEach(action -> {
            if (action.sourceSalesPointId() != null) {
                salesPointIds.add(action.sourceSalesPointId());
            }
            if (action.targetSalesPointId() != null) {
                salesPointIds.add(action.targetSalesPointId());
            }
        });
        List<StrategyCalculationSalesPointVO> activeRows = salesPointIds.isEmpty()
                ? List.of()
                : inputMapper.selectActiveSalesPoints(List.copyOf(salesPointIds));
        Set<Long> activeIds = activeRows.stream()
                .map(StrategyCalculationSalesPointVO::getSalesPointId)
                .collect(Collectors.toSet());
        salesPointIds.stream()
                .filter(id -> !activeIds.contains(id))
                .forEach(id -> changes.add(change(
                        StrategyExecutionConditionChangeType.SALES_POINT_INACTIVE,
                        "salesPointStatus",
                        "판매처 상태",
                        new StrategyExecutionConditionSubject(null, null, null, id),
                        "ACTIVE",
                        "INACTIVE_OR_MISSING",
                        "ACTIVE",
                        null,
                        null,
                        salesPointLabel(context, id)
                                + "가 더 이상 활성 판매처가 아닙니다."
                )));

        List<StrategyCalculationWarehouseRouteVO> routes = salesPointIds.isEmpty()
                ? List.of()
                : inputMapper.selectActiveWarehouseRoutes(List.copyOf(salesPointIds));
        candidate.actions().stream()
                .filter(action -> action.actionType() == StrategyType.REALLOCATION
                        || action.actionType() == StrategyType.RT_TRANSFER)
                .filter(action -> action.targetSalesPointId() != null
                        && action.targetWarehouseId() != null)
                .filter(action -> routes.stream().noneMatch(route ->
                        Objects.equals(
                                route.getSalesPointId(),
                                action.targetSalesPointId()
                        ) && Objects.equals(
                                route.getWarehouseId(),
                                action.targetWarehouseId()
                        )))
                .forEach(action -> changes.add(change(
                        StrategyExecutionConditionChangeType
                                .TRANSFER_ROUTE_UNAVAILABLE,
                        "transferRoute",
                        "재고 이동 경로",
                        new StrategyExecutionConditionSubject(
                                null,
                                null,
                                action.targetWarehouseId(),
                                action.targetSalesPointId()
                        ),
                        "ACTIVE",
                        "UNAVAILABLE",
                        "ACTIVE",
                        null,
                        null,
                        salesPointLabel(context, action.targetSalesPointId())
                                + "와 대상 창고 사이의 활성 경로가 없습니다."
                )));
    }

    private void detectCommercialChanges(
            ResolvedStrategySelection resolved,
            LocalDate businessDate,
            Long skuId,
            List<StrategyExecutionConditionChange> changes
    ) {
        List<StrategyCalculationCostVO> costs = inputMapper.selectEffectiveCosts(
                skuId, businessDate
        );
        BigDecimal currentUnitCost = costs.size() == 1
                ? costs.get(0).getUnitCost() : null;
        if (different(
                resolved.calculationContext().unitCost(), currentUnitCost
        )) {
            changes.add(change(
                    StrategyExecutionConditionChangeType.UNIT_COST_CHANGED,
                    "unitCost",
                    "상품 단위원가",
                    null,
                    resolved.calculationContext().unitCost(),
                    currentUnitCost,
                    null,
                    null,
                    "원",
                    "생성 이후 SKU 원가가 변경되었습니다."
            ));
        }

        Set<Long> salesPointIds = commercialSalesPointIds(resolved);
        if (salesPointIds.isEmpty()) return;
        List<StrategyCalculationPriceVO> prices = inputMapper.selectEffectivePrices(
                skuId, List.copyOf(salesPointIds), businessDate
        );
        for (Long salesPointId : salesPointIds) {
            detectPriceChanges(resolved, salesPointId, prices, changes);
        }
    }

    private static void detectPriceChanges(
            ResolvedStrategySelection resolved,
            Long salesPointId,
            List<StrategyCalculationPriceVO> prices,
            List<StrategyExecutionConditionChange> changes
    ) {
        var context = resolved.calculationContext();
        var generatedPoint = context.salesPoints().get(salesPointId);
        var generated = generatedPoint == null ? null : generatedPoint.price();
        List<StrategyCalculationPriceVO> matching = prices.stream()
                .filter(price -> Objects.equals(
                        price.getSalesPointId(), salesPointId
                ))
                .toList();
        StrategyExecutionConditionSubject subject =
                new StrategyExecutionConditionSubject(
                        null, null, null, salesPointId
                );
        if (generated == null || matching.size() != 1) {
            changes.add(change(
                    StrategyExecutionConditionChangeType.SELLING_PRICE_CHANGED,
                    "actualPrice",
                    "판매가",
                    subject,
                    generated == null ? null : generated.actualPrice(),
                    null,
                    null,
                    null,
                    "원",
                    salesPointLabel(context, salesPointId)
                            + "의 현재 판매가를 확정할 수 없습니다."
            ));
            return;
        }

        StrategyCalculationPriceVO current = matching.get(0);
        addMoneyChange(
                changes,
                StrategyExecutionConditionChangeType.SELLING_PRICE_CHANGED,
                "actualPrice",
                "판매가",
                subject,
                generated.actualPrice(),
                current.getActualPrice(),
                salesPointLabel(context, salesPointId)
                        + "의 판매가가 변경되었습니다."
        );
        addMoneyChange(
                changes,
                StrategyExecutionConditionChangeType.PAYMENT_FEE_CHANGED,
                "paymentFee",
                "결제 수수료",
                subject,
                generated.paymentFee(),
                current.getPaymentFee(),
                salesPointLabel(context, salesPointId)
                        + "의 결제 수수료가 변경되었습니다."
        );
        addMoneyChange(
                changes,
                StrategyExecutionConditionChangeType.LOGISTICS_COST_CHANGED,
                "logisticsCost",
                "판매 물류비",
                subject,
                generated.logisticsCost(),
                current.getLogisticsCost(),
                salesPointLabel(context, salesPointId)
                        + "의 판매 물류비가 변경되었습니다."
        );

        BigDecimal strategyPrice = strategyPrice(resolved, salesPointId);
        BigDecimal minimum = current.getMinimumSellingPrice();
        if (strategyPrice != null && (minimum == null
                || strategyPrice.compareTo(minimum) < 0)) {
            changes.add(change(
                    StrategyExecutionConditionChangeType
                            .MINIMUM_SELLING_PRICE_VIOLATED,
                    "strategyPrice",
                    "전략 판매가",
                    subject,
                    generated.minimumSellingPrice(),
                    minimum,
                    strategyPrice,
                    minimum,
                    "원",
                    "전략 판매가가 현재 최저 판매가 정책을 위반합니다."
            ));
        }
    }

    private static Set<Long> commercialSalesPointIds(
            ResolvedStrategySelection resolved
    ) {
        Set<Long> result = new LinkedHashSet<>();
        resolved.option().candidate().actions().forEach(action -> {
            addCommercialPoint(resolved, result, action.sourceSalesPointId());
            addCommercialPoint(resolved, result, action.targetSalesPointId());
        });
        return result;
    }

    private static void addCommercialPoint(
            ResolvedStrategySelection resolved,
            Set<Long> result,
            Long salesPointId
    ) {
        if (salesPointId == null) return;
        var point = resolved.calculationContext().salesPoints().get(salesPointId);
        if (point != null && point.price() != null) {
            result.add(salesPointId);
        }
    }

    private static BigDecimal strategyPrice(
            ResolvedStrategySelection resolved,
            Long salesPointId
    ) {
        return resolved.option().candidate().actions().stream()
                .filter(action -> action.actionType() == StrategyType.PRICE_DISCOUNT)
                .filter(action -> Objects.equals(
                        action.targetSalesPointId(), salesPointId
                ))
                .map(StrategyGenerationResult.Action::strategyPrice)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private static void addMoneyChange(
            List<StrategyExecutionConditionChange> changes,
            StrategyExecutionConditionChangeType type,
            String field,
            String label,
            StrategyExecutionConditionSubject subject,
            BigDecimal previous,
            BigDecimal current,
            String reason
    ) {
        if (!different(previous, current)) return;
        changes.add(change(
                type,
                field,
                label,
                subject,
                previous,
                current,
                null,
                null,
                "원",
                reason
        ));
    }

    private static Map<Long, BigDecimal> requiredInventory(
            StrategyGenerationResult.Candidate candidate
    ) {
        List<StrategyGenerationResult.Action> physical = candidate.actions().stream()
                .filter(action -> action.actionType() == StrategyType.REALLOCATION
                        || action.actionType() == StrategyType.RT_TRANSFER)
                .filter(action -> !action.lotAllocations().isEmpty())
                .toList();
        if (physical.isEmpty()) {
            physical = candidate.actions().stream()
                    .filter(action -> action.actionType()
                            == StrategyType.PRICE_DISCOUNT)
                    .filter(action -> !action.lotAllocations().isEmpty())
                    .toList();
        }
        Map<Long, BigDecimal> result = new LinkedHashMap<>();
        physical.stream().flatMap(action -> action.lotAllocations().stream())
                .forEach(lot -> result.merge(
                        lot.inventoryBalanceId(), lot.quantity(), BigDecimal::add
                ));
        return result;
    }

    private static Map<Long, StrategyCalculationContext.InventoryLot>
            snapshotInventory(StrategyCalculationContext context) {
        Map<Long, StrategyCalculationContext.InventoryLot> result =
                new LinkedHashMap<>();
        context.evaluationInventory().forEach(lot -> result.put(
                lot.inventoryBalanceId(), lot
        ));
        context.referenceInventory().forEach(lot -> result.putIfAbsent(
                lot.inventoryBalanceId(), lot
        ));
        return result;
    }

    private static boolean allocationIdentityMatches(
            StrategyGenerationResult.Candidate candidate,
            StrategyCalculationInventoryVO current
    ) {
        return candidate.actions().stream()
                .filter(action -> action.lotAllocations().stream().anyMatch(lot ->
                        Objects.equals(
                                lot.inventoryBalanceId(),
                                current.getInventoryBalanceId()
                        )))
                .anyMatch(action -> action.lotAllocations().stream().anyMatch(lot ->
                                Objects.equals(lot.lotId(), current.getLotId()))
                        && Objects.equals(
                        action.sourceWarehouseId(), current.getWarehouseId()
                ) && (action.sourceSalesPointId() == null
                        || Objects.equals(
                        action.sourceSalesPointId(),
                        current.effectiveSalesPointId()
                )));
    }

    private static String expectedAllocationLocation(
            StrategyGenerationResult.Candidate candidate,
            Long inventoryBalanceId
    ) {
        return candidate.actions().stream()
                .filter(action -> action.lotAllocations().stream().anyMatch(lot ->
                        Objects.equals(lot.inventoryBalanceId(), inventoryBalanceId)
                ))
                .findFirst()
                .map(action -> locationValue(
                        action.sourceWarehouseId(),
                        action.sourceSalesPointId(),
                        action.lotAllocations().stream()
                                .filter(lot -> Objects.equals(
                                        lot.inventoryBalanceId(),
                                        inventoryBalanceId
                                ))
                                .map(StrategyGenerationResult.LotAllocation::lotId)
                                .findFirst()
                                .orElse(null)
                ))
                .orElse(null);
    }

    private static boolean isSellableAt(
            StrategyCalculationInventoryVO inventory,
            LocalDate date
    ) {
        return "AVAILABLE".equals(inventory.getLotStatus())
                && (inventory.getExpiryDate() == null
                || !date.isAfter(inventory.getExpiryDate()))
                && (inventory.getSaleStopDate() == null
                || date.isBefore(inventory.getSaleStopDate()));
    }

    private static String sellableState(
            StrategyCalculationInventoryVO inventory,
            LocalDate date
    ) {
        if (!"AVAILABLE".equals(inventory.getLotStatus())) {
            return inventory.getLotStatus();
        }
        if (inventory.getExpiryDate() != null
                && date.isAfter(inventory.getExpiryDate())) {
            return "EXPIRED";
        }
        if (inventory.getSaleStopDate() != null
                && !date.isBefore(inventory.getSaleStopDate())) {
            return "SALE_STOPPED";
        }
        return "AVAILABLE";
    }

    private static LocalDate sellableEndDate(
            StrategyCalculationInventoryVO inventory
    ) {
        LocalDate expiry = inventory.getExpiryDate();
        LocalDate saleStop = inventory.getSaleStopDate() == null
                ? null : inventory.getSaleStopDate().minusDays(1);
        return earlier(expiry, saleStop);
    }

    private static LocalDate sellableEndDate(
            StrategyCalculationContext.InventoryLot inventory
    ) {
        LocalDate expiry = inventory.expiryDate();
        LocalDate saleStop = inventory.saleStopDate() == null
                ? null : inventory.saleStopDate().minusDays(1);
        return earlier(expiry, saleStop);
    }

    private static LocalDate earlier(LocalDate left, LocalDate right) {
        if (left == null) return right;
        if (right == null) return left;
        return left.isBefore(right) ? left : right;
    }

    private static BigDecimal available(StrategyCalculationInventoryVO row) {
        BigDecimal onHand = row.getOnHandQty() == null
                ? BigDecimal.ZERO : row.getOnHandQty();
        return onHand.max(BigDecimal.ZERO);
    }

    private static boolean different(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) return !Objects.equals(left, right);
        return left.compareTo(right) != 0;
    }

    private static StrategyExecutionConditionSubject subject(
            StrategyCalculationInventoryVO value
    ) {
        return new StrategyExecutionConditionSubject(
                value.getInventoryBalanceId(),
                value.getLotId(),
                value.getWarehouseId(),
                value.effectiveSalesPointId()
        );
    }

    private static StrategyExecutionConditionSubject subject(
            StrategyCalculationContext.InventoryLot value
    ) {
        if (value == null) return null;
        return new StrategyExecutionConditionSubject(
                value.inventoryBalanceId(),
                value.lotId(),
                value.warehouseId(),
                value.effectiveSalesPointId()
        );
    }

    private static String inventoryLabel(
            StrategyCalculationContext.InventoryLot value,
            Long inventoryBalanceId
    ) {
        return value != null && value.lotId() != null
                ? "LOT " + value.lotId()
                : "재고 " + inventoryBalanceId;
    }

    private static String lotLabel(StrategyCalculationInventoryVO value) {
        return value.getLotId() == null
                ? "배정 재고"
                : "LOT " + value.getLotId();
    }

    private static String salesPointLabel(
            StrategyCalculationContext context,
            Long salesPointId
    ) {
        var point = context.salesPoints().get(salesPointId);
        return point == null
                ? "판매처 " + salesPointId
                : point.salesPointName() + "(ID: " + salesPointId + ")";
    }

    private static String locationValue(
            Long warehouseId,
            Long salesPointId,
            Long lotId
    ) {
        return "warehouseId=" + warehouseId
                + ", salesPointId=" + salesPointId
                + ", lotId=" + lotId;
    }

    private static StrategyExecutionConditionChange change(
            StrategyExecutionConditionChangeType type,
            String field,
            String label,
            StrategyExecutionConditionSubject subject,
            Object previousValue,
            Object currentValue,
            Object requestedValue,
            Object suggestedValue,
            String unit,
            String reason
    ) {
        return new StrategyExecutionConditionChange(
                type,
                field,
                label,
                subject,
                previousValue,
                currentValue,
                requestedValue,
                suggestedValue,
                unit,
                reason
        );
    }
}
