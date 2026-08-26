package com.stockit.backend.feature.strategy.approval;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.strategy.calculation.candidate.calculator.InventoryTransferCostCalculator;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.calculation.mapper.StrategyCalculationInputMapper;
import com.stockit.backend.feature.strategy.calculation.vo.StrategyCalculationSkuVO;
import com.stockit.backend.feature.strategy.calculation.vo.StrategyCalculationTransferCostPolicyVO;
import com.stockit.backend.feature.strategy.calculation.vo.StrategyCalculationTransferRouteVO;
import com.stockit.backend.feature.strategy.domain.StrategyType;
import com.stockit.backend.feature.strategy.result.StrategyGenerationResult;

/** 최종 선택 직전 RT_TRANSFER의 경로·요율·중량 Snapshot 최신성을 검증한다. */
@Component
public class StrategyTransferInputFreshnessValidator {

    private final StrategyCalculationInputMapper inputMapper;
    private final InventoryTransferCostCalculator transferCostCalculator;

    public StrategyTransferInputFreshnessValidator(
            StrategyCalculationInputMapper inputMapper,
            InventoryTransferCostCalculator transferCostCalculator
    ) {
        this.inputMapper = inputMapper;
        this.transferCostCalculator = transferCostCalculator;
    }

    public void validate(ResolvedStrategySelection resolved) {
        List<StrategyGenerationResult.Action> transferActions = resolved.option()
                .candidate().actions().stream()
                .filter(action -> action.actionType() == StrategyType.RT_TRANSFER)
                .toList();
        if (transferActions.isEmpty()) {
            return;
        }

        validateSkuWeight(resolved.calculationContext());
        Map<Long, StrategyCalculationTransferRouteVO> currentRoutes = currentRoutes(
                transferActions
        );
        StrategyCalculationTransferCostPolicyVO currentPolicy = currentPolicy(
                resolved.option().candidate().startDate()
        );

        for (StrategyGenerationResult.Action action : transferActions) {
            validateAction(
                    action,
                    resolved.calculationContext(),
                    currentRoutes,
                    currentPolicy
            );
        }
    }

    private void validateSkuWeight(StrategyCalculationContext context) {
        StrategyCalculationSkuVO current = inputMapper.selectActiveSku(
                context.sku().skuId()
        );
        if (current == null
                || different(current.getNetWeight(), context.sku().netWeight())
                || !sameUnit(current.getWeightUnit(), context.sku().weightUnit())) {
            conflict(
                    "생성 이후 SKU 중량 정보가 변경되었습니다. 전략을 다시 생성해 주세요."
            );
        }
    }

    private Map<Long, StrategyCalculationTransferRouteVO> currentRoutes(
            List<StrategyGenerationResult.Action> actions
    ) {
        if (actions.stream()
                .map(StrategyGenerationResult.Action::movementCost)
                .anyMatch(Objects::isNull)) {
            conflict("재고 이동비 계산 근거가 없습니다. 전략을 다시 생성해 주세요.");
        }
        List<Long> routeIds = actions.stream()
                .map(StrategyGenerationResult.Action::movementCost)
                .map(StrategyGenerationResult.MovementCost::transferRouteId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList();
        if (routeIds.isEmpty()) {
            conflict("재고 이동비 계산 근거가 없습니다. 전략을 다시 생성해 주세요.");
        }
        List<StrategyCalculationTransferRouteVO> rows =
                inputMapper.selectActiveTransferRoutesByIds(routeIds);
        if (rows.size() != routeIds.size()) {
            conflict(
                    "생성 이후 재고 이동 경로가 비활성화되었습니다. 전략을 다시 생성해 주세요."
            );
        }
        return rows.stream().collect(Collectors.toUnmodifiableMap(
                StrategyCalculationTransferRouteVO::getTransferRouteId,
                Function.identity()
        ));
    }

    private StrategyCalculationTransferCostPolicyVO currentPolicy(LocalDate startDate) {
        List<StrategyCalculationTransferCostPolicyVO> policies =
                inputMapper.selectTransferCostPolicies(startDate, startDate);
        if (policies.size() != 1) {
            conflict(
                    "현재 적용 가능한 재고 이동비 정책을 하나로 확정할 수 없습니다. 전략을 다시 생성해 주세요."
            );
        }
        return policies.get(0);
    }

    private void validateAction(
            StrategyGenerationResult.Action action,
            StrategyCalculationContext context,
            Map<Long, StrategyCalculationTransferRouteVO> currentRoutes,
            StrategyCalculationTransferCostPolicyVO currentPolicy
    ) {
        StrategyGenerationResult.MovementCost snapshot = action.movementCost();
        if (snapshot == null || snapshot.transferRouteId() == null
                || snapshot.transferCostPolicyId() == null
                || snapshot.weightKg() == null || snapshot.distanceKm() == null
                || snapshot.costPerKgKm() == null || snapshot.estimatedCost() == null
                || action.actionQuantity() == null
                || action.estimatedActionCost() == null) {
            conflict("재고 이동비 계산 근거가 없습니다. 전략을 다시 생성해 주세요.");
        }

        StrategyCalculationTransferRouteVO route = currentRoutes.get(
                snapshot.transferRouteId()
        );
        if (route == null
                || !samePhysicalLocation(
                        action.sourceWarehouseId(), action.sourceSalesPointId(),
                        route.getSourceWarehouseId(), route.getSourceSalesPointId()
                )
                || !samePhysicalLocation(
                        action.targetWarehouseId(), action.targetSalesPointId(),
                        route.getDestinationWarehouseId(),
                        route.getDestinationSalesPointId()
                )
                || different(route.getDistanceKm(), snapshot.distanceKm())) {
            conflict(
                    "생성 이후 재고 이동 경로가 변경되었습니다. 전략을 다시 생성해 주세요."
            );
        }

        if (!Objects.equals(
                currentPolicy.getTransferCostPolicyId(),
                snapshot.transferCostPolicyId()
        ) || different(
                currentPolicy.getCostPerKgKm(), snapshot.costPerKgKm()
        )) {
            conflict(
                    "생성 이후 재고 이동비 정책이 변경되었습니다. 전략을 다시 생성해 주세요."
            );
        }

        BigDecimal unitWeightKg;
        BigDecimal expectedWeight;
        BigDecimal expectedCost;
        try {
            unitWeightKg = transferCostCalculator.unitWeightKg(
                    context.sku().netWeight(), context.sku().weightUnit()
            );
            expectedWeight = transferCostCalculator.totalWeightKg(
                    unitWeightKg, action.actionQuantity()
            );
            expectedCost = transferCostCalculator.estimatedCost(
                    expectedWeight,
                    route.getDistanceKm(),
                    currentPolicy.getCostPerKgKm()
            );
        } catch (RuntimeException exception) {
            conflict(
                    "현재 재고 이동비를 다시 계산할 수 없습니다. 전략을 다시 생성해 주세요."
            );
            return;
        }

        if (different(expectedWeight, snapshot.weightKg())
                || different(expectedCost, snapshot.estimatedCost())
                || different(expectedCost, action.estimatedActionCost())) {
            conflict(
                    "생성 당시 재고 이동비와 현재 계산 결과가 일치하지 않습니다. 전략을 다시 생성해 주세요."
            );
        }
    }

    /** Action의 판매처 ID는 할당 의미일 수 있으므로 창고 ID가 있으면 물리 위치로 우선한다. */
    private static boolean samePhysicalLocation(
            Long actionWarehouseId,
            Long actionSalesPointId,
            Long currentWarehouseId,
            Long currentSalesPointId
    ) {
        if (actionWarehouseId != null) {
            return Objects.equals(actionWarehouseId, currentWarehouseId)
                    && currentSalesPointId == null;
        }
        return currentWarehouseId == null
                && Objects.equals(actionSalesPointId, currentSalesPointId);
    }

    private static boolean sameUnit(String left, String right) {
        return left != null && right != null
                && left.trim().toUpperCase(Locale.ROOT).equals(
                        right.trim().toUpperCase(Locale.ROOT)
                );
    }

    private static boolean different(BigDecimal left, BigDecimal right) {
        return left == null || right == null || left.compareTo(right) != 0;
    }

    private static void conflict(String message) {
        throw new AppException(ErrorCode.AI_STRATEGY_SELECTION_CONFLICT, message);
    }
}
