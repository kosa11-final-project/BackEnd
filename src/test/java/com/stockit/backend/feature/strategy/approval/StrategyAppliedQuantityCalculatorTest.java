package com.stockit.backend.feature.strategy.approval;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.stockit.backend.feature.strategy.domain.StrategyType;
import com.stockit.backend.feature.strategy.result.StrategyGenerationResult;

class StrategyAppliedQuantityCalculatorTest {

    private final StrategyAppliedQuantityCalculator calculator =
            new StrategyAppliedQuantityCalculator();

    @Test
    void doesNotDoubleCountChannelActionAfterInventoryMovement() {
        StrategyGenerationResult.Candidate candidate = candidate(
                List.of(StrategyType.CHANNEL_EXPANSION, StrategyType.RT_TRANSFER),
                List.of(
                        action(
                                StrategyType.RT_TRANSFER,
                                "30",
                                List.of(allocation(100L, 1000L, "30"))
                        ),
                        action(StrategyType.CHANNEL_EXPANSION, "30", List.of())
                )
        );

        assertThat(calculator.calculate(candidate)).isEqualByComparingTo("30");
    }

    @Test
    void sumsPhysicalLotAllocationsSplitAcrossWarehouses() {
        StrategyGenerationResult.Candidate candidate = candidate(
                List.of(StrategyType.PRICE_DISCOUNT),
                List.of(
                        action(
                                StrategyType.PRICE_DISCOUNT,
                                "20",
                                List.of(allocation(100L, 1000L, "20"))
                        ),
                        action(
                                StrategyType.PRICE_DISCOUNT,
                                "10",
                                List.of(allocation(200L, 2000L, "10"))
                        )
                )
        );

        assertThat(calculator.calculate(candidate)).isEqualByComparingTo("30");
    }

    private static StrategyGenerationResult.Candidate candidate(
            List<StrategyType> types,
            List<StrategyGenerationResult.Action> actions
    ) {
        return new StrategyGenerationResult.Candidate(
                "CAND-1",
                types,
                LocalDate.of(2026, 8, 25),
                LocalDate.of(2026, 8, 31),
                actions,
                List.of(),
                new StrategyGenerationResult.Preference(1, 1, 100),
                new BigDecimal("100")
        );
    }

    private static StrategyGenerationResult.Action action(
            StrategyType type,
            String quantity,
            List<StrategyGenerationResult.LotAllocation> allocations
    ) {
        return new StrategyGenerationResult.Action(
                type,
                1L,
                10L,
                2L,
                20L,
                new BigDecimal(quantity),
                BigDecimal.ZERO,
                type == StrategyType.PRICE_DISCOUNT
                        ? new BigDecimal("8500") : null,
                type == StrategyType.PRICE_DISCOUNT
                        ? new BigDecimal("0.15") : null,
                allocations
        );
    }

    private static StrategyGenerationResult.LotAllocation allocation(
            Long inventoryBalanceId,
            Long lotId,
            String quantity
    ) {
        return new StrategyGenerationResult.LotAllocation(
                inventoryBalanceId, lotId, new BigDecimal(quantity), 1
        );
    }
}
