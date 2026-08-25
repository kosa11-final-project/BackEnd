package com.stockit.backend.feature.strategy.calculation.candidate.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;

class SafetyStockPolicyResolverTest {

    private final SafetyStockPolicyResolver resolver = new SafetyStockPolicyResolver();

    @Test
    void choosesMostSpecificPolicyAndUsesSmallestIdForTie() {
        List<StrategyCalculationContext.InventoryPolicy> policies = List.of(
                policy(9L, null, null, "1"),
                policy(5L, 501L, 10L, "5"),
                policy(3L, 501L, 10L, "7")
        );

        SafetyStockPolicyResolver.Resolution result = resolver.resolve(
                policies,
                501L,
                10L
        );

        assertThat(result.inventoryPolicyId()).isEqualTo(3L);
        assertThat(result.safetyStockQty()).isEqualByComparingTo("7");
        assertThat(result.defaultedToZero()).isFalse();
    }

    @Test
    void explicitlyDefaultsMissingPolicyToZero() {
        SafetyStockPolicyResolver.Resolution result = resolver.resolve(
                List.of(),
                501L,
                10L
        );

        assertThat(result.safetyStockQty()).isEqualByComparingTo("0");
        assertThat(result.defaultedToZero()).isTrue();
    }

    private static StrategyCalculationContext.InventoryPolicy policy(
            Long id,
            Long warehouseId,
            Long salesPointId,
            String safetyStock
    ) {
        return new StrategyCalculationContext.InventoryPolicy(
                id,
                warehouseId,
                salesPointId,
                salesPointId,
                new BigDecimal(safetyStock),
                null,
                null,
                null
        );
    }
}
