package com.stockit.backend.feature.strategy.calculation.candidate.policy;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext.InventoryPolicy;

/** 창고·판매처에 가장 구체적으로 맞는 안전재고 정책을 선택한다. */
@Component
public class SafetyStockPolicyResolver {

    public Resolution resolve(
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
                .map(scored -> new Resolution(
                        scored.policy().safetyStockQty(),
                        false,
                        scored.policy().inventoryPolicyId()
                ))
                .orElseGet(() -> new Resolution(BigDecimal.ZERO, true, null));
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

    public record Resolution(
            BigDecimal safetyStockQty,
            boolean defaultedToZero,
            Long inventoryPolicyId
    ) {
    }

    private record ScoredPolicy(InventoryPolicy policy, int score) {
    }
}
