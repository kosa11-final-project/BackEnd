package com.stockit.backend.feature.strategy.calculation.engine;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext.InventoryPolicy;

/** 현재 재고 위치에 가장 구체적으로 맞는 보관·폐기 처리비 정책을 선택한다. */
final class InventoryCostPolicyResolver {

    private InventoryCostPolicyResolver() {
    }

    static Cost resolve(
            List<InventoryPolicy> policies,
            Long warehouseId,
            Long salesPointId
    ) {
        return policies.stream()
                .map(policy -> new ScoredPolicy(
                        policy,
                        matchScore(policy, warehouseId, salesPointId)
                ))
                .filter(scored -> scored.score() >= 0)
                .max(Comparator.comparingInt(ScoredPolicy::score)
                        .thenComparing(scored -> -scored.policy().inventoryPolicyId()))
                .map(scored -> new Cost(
                        nonNegative(scored.policy().dailyUnitHoldingCost()),
                        nonNegative(scored.policy().unitDisposalCost())
                ))
                // 누락 비용은 절감액을 만들어내지 않도록 0으로 보수적 처리한다.
                .orElseGet(() -> new Cost(BigDecimal.ZERO, BigDecimal.ZERO));
    }

    private static int matchScore(
            InventoryPolicy policy,
            Long warehouseId,
            Long salesPointId
    ) {
        int warehouseScore;
        if (policy.warehouseId() == null) {
            warehouseScore = 1;
        } else if (Objects.equals(policy.warehouseId(), warehouseId)) {
            warehouseScore = 3;
        } else {
            return -1;
        }

        boolean globalSalesPoint = policy.stockSalesPointId() == null
                && policy.allocatedSalesPointId() == null;
        int salesPointScore;
        if (globalSalesPoint) {
            salesPointScore = 1;
        } else if (salesPointId != null
                && (Objects.equals(policy.stockSalesPointId(), salesPointId)
                || Objects.equals(policy.allocatedSalesPointId(), salesPointId))) {
            salesPointScore = 3;
        } else {
            return -1;
        }
        return warehouseScore + salesPointScore;
    }

    private static BigDecimal nonNegative(BigDecimal value) {
        return value == null || value.signum() < 0 ? BigDecimal.ZERO : value;
    }

    record Cost(BigDecimal dailyUnitHoldingCost, BigDecimal unitDisposalCost) {
    }

    private record ScoredPolicy(InventoryPolicy policy, int score) {
    }
}
