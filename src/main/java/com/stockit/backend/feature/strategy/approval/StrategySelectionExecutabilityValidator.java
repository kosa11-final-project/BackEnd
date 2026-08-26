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

import org.springframework.stereotype.Component;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.strategy.calculation.candidate.policy.SafetyStockPolicyResolver;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext.InventoryPolicy;
import com.stockit.backend.feature.strategy.calculation.mapper.StrategyCalculationInputMapper;
import com.stockit.backend.feature.strategy.calculation.vo.StrategyCalculationCostVO;
import com.stockit.backend.feature.strategy.calculation.vo.StrategyCalculationInventoryVO;
import com.stockit.backend.feature.strategy.calculation.vo.StrategyCalculationPolicyVO;
import com.stockit.backend.feature.strategy.calculation.vo.StrategyCalculationPriceVO;
import com.stockit.backend.feature.strategy.calculation.vo.StrategyCalculationWarehouseRouteVO;
import com.stockit.backend.feature.strategy.domain.StrategyType;
import com.stockit.backend.feature.strategy.result.StrategyGenerationResult;

/** 최종 저장 직전 현재 DB 입력으로 선택 전략의 실행 가능성만 재검증한다. */
@Component
public class StrategySelectionExecutabilityValidator {

    private final StrategyCalculationInputMapper inputMapper;
    private final SafetyStockPolicyResolver safetyStockResolver;
    private final StrategyTransferInputFreshnessValidator transferFreshnessValidator;

    public StrategySelectionExecutabilityValidator(
            StrategyCalculationInputMapper inputMapper,
            SafetyStockPolicyResolver safetyStockResolver,
            StrategyTransferInputFreshnessValidator transferFreshnessValidator
    ) {
        this.inputMapper = inputMapper;
        this.safetyStockResolver = safetyStockResolver;
        this.transferFreshnessValidator = transferFreshnessValidator;
    }

    public void validate(ResolvedStrategySelection resolved, LocalDate businessDate) {
        StrategyGenerationResult.Candidate candidate = resolved.option().candidate();
        Long skuId = resolved.calculationContext().sku().skuId();
        List<StrategyCalculationInventoryVO> inventory = inputMapper.selectInventory(skuId);
        Map<Long, StrategyCalculationInventoryVO> byId = new LinkedHashMap<>();
        inventory.forEach(row -> byId.put(row.getInventoryBalanceId(), row));

        Map<Long, BigDecimal> requiredByBalance = requiredInventory(candidate);
        for (Map.Entry<Long, BigDecimal> required : requiredByBalance.entrySet()) {
            StrategyCalculationInventoryVO current = byId.get(required.getKey());
            if (current == null || current.getLotId() == null) {
                conflict("최종 선택 수량만큼 현재 가용재고가 남아 있지 않습니다.");
            }
            if (!isSellableAt(current, businessDate)) {
                conflict("최종 선택에 포함된 LOT가 현재 판매 가능한 상태가 아닙니다.");
            }
            if (available(current).compareTo(required.getValue()) < 0) {
                conflict("최종 선택 수량만큼 현재 가용재고가 남아 있지 않습니다.");
            }
            validateAllocationIdentity(candidate, current);
        }
        validateSafetyStock(
                inventory,
                byId,
                requiredByBalance,
                businessDate,
                skuId,
                resolved.calculationContext().sourceSalesPointId()
        );
        validateSalesPointsAndRoutes(candidate);
        validateCommercialInputs(resolved, businessDate, skuId);
        transferFreshnessValidator.validate(resolved);
    }

    private void validateSafetyStock(
            List<StrategyCalculationInventoryVO> inventory,
            Map<Long, StrategyCalculationInventoryVO> inventoryById,
            Map<Long, BigDecimal> requiredByBalance,
            LocalDate businessDate,
            Long skuId,
            Long sourceSalesPointId
    ) {
        List<StrategyCalculationPolicyVO> policies = inputMapper.selectEffectivePolicies(
                skuId, businessDate
        );
        List<InventoryPolicy> policyInputs = policies.stream()
                .map(StrategySelectionExecutabilityValidator::toPolicy)
                .toList();
        Set<Long> requiredWarehouseIds = requiredByBalance.keySet().stream()
                .map(inventoryById::get)
                .filter(Objects::nonNull)
                .map(StrategyCalculationInventoryVO::getWarehouseId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        for (Long warehouseId : requiredWarehouseIds) {
            BigDecimal safety = safetyStockResolver.resolve(
                    policyInputs, warehouseId, sourceSalesPointId
            ).safetyStockQty();
            BigDecimal available = inventory.stream()
                    .filter(row -> Objects.equals(row.getWarehouseId(), warehouseId))
                    .filter(row -> matchesSource(row, sourceSalesPointId))
                    .filter(row -> isSellableAt(row, businessDate))
                    .map(StrategySelectionExecutabilityValidator::available)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal requested = requiredByBalance.entrySet().stream()
                    .filter(entry -> Objects.equals(
                            inventoryById.get(entry.getKey()).getWarehouseId(),
                            warehouseId
                    ))
                    .map(Map.Entry::getValue)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (available.subtract(requested).compareTo(safety) < 0) {
                conflict("최종 선택 수량이 현재 안전재고를 침해합니다.");
            }
        }
    }

    private void validateSalesPointsAndRoutes(
            StrategyGenerationResult.Candidate candidate
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
        if (!salesPointIds.isEmpty()
                && inputMapper.selectActiveSalesPoints(List.copyOf(salesPointIds)).size()
                != salesPointIds.size()) {
            conflict("전략의 출발 또는 대상 판매처가 더 이상 활성 상태가 아닙니다.");
        }
        List<StrategyCalculationWarehouseRouteVO> routes = salesPointIds.isEmpty()
                ? List.of()
                : inputMapper.selectActiveWarehouseRoutes(List.copyOf(salesPointIds));
        for (StrategyGenerationResult.Action action : candidate.actions()) {
            if ((action.actionType() != StrategyType.REALLOCATION
                    && action.actionType() != StrategyType.RT_TRANSFER)
                    || action.targetSalesPointId() == null
                    || action.targetWarehouseId() == null) {
                continue;
            }
            boolean routeExists = routes.stream().anyMatch(route ->
                    Objects.equals(route.getSalesPointId(), action.targetSalesPointId())
                            && Objects.equals(
                            route.getWarehouseId(), action.targetWarehouseId()
                    ));
            if (!routeExists) {
                conflict("전략 대상 판매처와 창고의 활성 이동 경로를 찾을 수 없습니다.");
            }
        }
    }

    private void validateCommercialInputs(
            ResolvedStrategySelection resolved,
            LocalDate businessDate,
            Long skuId
    ) {
        List<StrategyCalculationCostVO> costs = inputMapper.selectEffectiveCosts(
                skuId, businessDate
        );
        if (costs.size() != 1 || costs.get(0).getUnitCost() == null
                || costs.get(0).getUnitCost().compareTo(
                resolved.calculationContext().unitCost()) != 0) {
            conflict("생성 이후 SKU 원가가 변경되어 전략을 다시 계산해야 합니다.");
        }
        Set<Long> commercialPoints = new LinkedHashSet<>();
        resolved.option().candidate().actions().forEach(action -> {
            addCommercialPoint(resolved, commercialPoints, action.sourceSalesPointId());
            addCommercialPoint(resolved, commercialPoints, action.targetSalesPointId());
        });
        if (commercialPoints.isEmpty()) return;
        List<StrategyCalculationPriceVO> prices = inputMapper.selectEffectivePrices(
                skuId, List.copyOf(commercialPoints), businessDate
        );
        for (Long salesPointId : commercialPoints) {
            List<StrategyCalculationPriceVO> current = prices.stream()
                    .filter(price -> Objects.equals(
                            price.getSalesPointId(), salesPointId
                    ))
                    .toList();
            if (current.size() != 1) {
                conflict("현재 적용 가능한 판매가를 하나로 확정할 수 없습니다.");
            }
            BigDecimal strategyPrice = resolved.option().candidate().actions().stream()
                    .filter(action -> action.actionType()
                            == StrategyType.PRICE_DISCOUNT)
                    .filter(action -> Objects.equals(
                            action.targetSalesPointId(), salesPointId
                    ))
                    .map(StrategyGenerationResult.Action::strategyPrice)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            var generatedSalesPoint = resolved.calculationContext()
                    .salesPoints().get(salesPointId);
            var generatedPrice = generatedSalesPoint == null
                    ? null : generatedSalesPoint.price();
            StrategyCalculationPriceVO currentPrice = current.get(0);
            if (generatedPrice == null
                    || different(currentPrice.getActualPrice(), generatedPrice.actualPrice())
                    || different(currentPrice.getPaymentFee(), generatedPrice.paymentFee())
                    || different(currentPrice.getLogisticsCost(), generatedPrice.logisticsCost())) {
                conflict("생성 이후 판매가 또는 변동비가 변경되어 전략을 다시 계산해야 합니다.");
            }
            BigDecimal minimum = currentPrice.getMinimumSellingPrice();
            if (strategyPrice != null && (minimum == null
                    || strategyPrice.compareTo(minimum) < 0)) {
                conflict("최종 전략 판매가가 현재 최저 판매가 정책을 위반합니다.");
            }
        }
    }

    private static void addCommercialPoint(
            ResolvedStrategySelection resolved,
            Set<Long> values,
            Long salesPointId
    ) {
        if (salesPointId == null) return;
        var point = resolved.calculationContext().salesPoints().get(salesPointId);
        if (point != null && point.price() != null) {
            values.add(salesPointId);
        }
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

    private static void validateAllocationIdentity(
            StrategyGenerationResult.Candidate candidate,
            StrategyCalculationInventoryVO current
    ) {
        List<StrategyGenerationResult.Action> matched = new ArrayList<>();
        candidate.actions().stream()
                .filter(action -> action.lotAllocations().stream().anyMatch(lot ->
                        Objects.equals(
                                lot.inventoryBalanceId(), current.getInventoryBalanceId()
                        )))
                .forEach(matched::add);
        boolean valid = matched.stream().anyMatch(action ->
                action.lotAllocations().stream().anyMatch(lot ->
                        Objects.equals(lot.lotId(), current.getLotId()))
                        && Objects.equals(
                        action.sourceWarehouseId(), current.getWarehouseId()
                ) && (action.sourceSalesPointId() == null
                        || Objects.equals(
                        action.sourceSalesPointId(), current.effectiveSalesPointId()
                )));
        if (!valid) {
            conflict("배정 재고의 LOT 또는 출발 위치가 생성 이후 변경되었습니다.");
        }
    }

    private static InventoryPolicy toPolicy(StrategyCalculationPolicyVO policy) {
        return new InventoryPolicy(
                policy.getInventoryPolicyId(),
                policy.getWarehouseId(),
                policy.getStockSalesPointId(),
                policy.getAllocatedSalesPointId(),
                policy.getSafetyStockQty(),
                policy.getTargetStockQty(),
                policy.getDailyUnitHoldingCost(),
                policy.getUnitDisposalCost()
        );
    }

    private static boolean matchesSource(
            StrategyCalculationInventoryVO inventory,
            Long sourceSalesPointId
    ) {
        return sourceSalesPointId == null
                ? inventory.isPublicUnassigned()
                : Objects.equals(
                        inventory.effectiveSalesPointId(), sourceSalesPointId
                );
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

    private static BigDecimal available(StrategyCalculationInventoryVO row) {
        BigDecimal onHand = row.getOnHandQty() == null
                ? BigDecimal.ZERO : row.getOnHandQty();
        // 프로젝트 재고 계약에서 on_hand_qty는 이미 예약분을 제외한 가용재고다.
        return onHand.max(BigDecimal.ZERO);
    }

    private static boolean different(BigDecimal left, BigDecimal right) {
        return left == null || right == null || left.compareTo(right) != 0;
    }

    private static void conflict(String message) {
        throw new AppException(ErrorCode.AI_STRATEGY_SELECTION_CONFLICT, message);
    }
}
