package com.stockit.backend.feature.strategy.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.calculation.mapper.StrategyCalculationInputMapper;
import com.stockit.backend.feature.strategy.calculation.vo.StrategyCalculationCostVO;
import com.stockit.backend.feature.strategy.calculation.vo.StrategyCalculationInventoryVO;
import com.stockit.backend.feature.strategy.domain.StrategyType;
import com.stockit.backend.feature.strategy.result.StrategyGenerationResult;

class StrategySelectionExecutabilityValidatorTest {

    @Test
    void rejectsSelectionWhenCurrentAvailableQuantityHasDecreased() {
        StrategyCalculationInputMapper mapper = mock(
                StrategyCalculationInputMapper.class
        );
        ResolvedStrategySelection resolved = mock(ResolvedStrategySelection.class);
        StrategyCalculationContext context = mock(StrategyCalculationContext.class);
        StrategyCalculationContext.Sku sku = mock(StrategyCalculationContext.Sku.class);
        StrategyGenerationResult.Option option = mock(
                StrategyGenerationResult.Option.class
        );
        StrategyGenerationResult.Candidate candidate = candidate("10");
        StrategyCalculationInventoryVO current = inventory("5");
        when(resolved.option()).thenReturn(option);
        when(resolved.calculationContext()).thenReturn(context);
        when(option.candidate()).thenReturn(candidate);
        when(context.sku()).thenReturn(sku);
        when(sku.skuId()).thenReturn(100L);
        when(mapper.selectInventory(100L)).thenReturn(List.of(current));

        StrategySelectionExecutabilityValidator validator =
                new StrategySelectionExecutabilityValidator(mapper);

        assertThatThrownBy(() -> validator.validate(
                resolved, LocalDate.of(2026, 8, 25)
        )).isInstanceOfSatisfying(
                AppException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.AI_STRATEGY_SELECTION_CONFLICT)
        );
    }

    @Test
    void doesNotSubtractReservedQuantityFromAlreadyAvailableOnHand() {
        StrategyCalculationInputMapper mapper = mock(
                StrategyCalculationInputMapper.class
        );
        ResolvedStrategySelection resolved = mock(ResolvedStrategySelection.class);
        StrategyCalculationContext context = mock(StrategyCalculationContext.class);
        StrategyCalculationContext.Sku sku = mock(StrategyCalculationContext.Sku.class);
        StrategyGenerationResult.Option option = mock(
                StrategyGenerationResult.Option.class
        );
        StrategyGenerationResult.Candidate candidate = candidateWithoutSalesPoint("10");
        StrategyCalculationInventoryVO current = inventory("10");
        current.setReservedQty(new BigDecimal("4"));
        StrategyCalculationCostVO cost = new StrategyCalculationCostVO();
        cost.setUnitCost(new BigDecimal("5000"));
        when(resolved.option()).thenReturn(option);
        when(resolved.calculationContext()).thenReturn(context);
        when(option.candidate()).thenReturn(candidate);
        when(context.sku()).thenReturn(sku);
        when(context.unitCost()).thenReturn(new BigDecimal("5000"));
        when(sku.skuId()).thenReturn(100L);
        when(mapper.selectInventory(100L)).thenReturn(List.of(current));
        when(mapper.selectEffectivePolicies(
                100L, LocalDate.of(2026, 8, 25)
        )).thenReturn(List.of());
        when(mapper.selectEffectiveCosts(
                100L, LocalDate.of(2026, 8, 25)
        )).thenReturn(List.of(cost));

        StrategySelectionExecutabilityValidator validator =
                new StrategySelectionExecutabilityValidator(mapper);

        validator.validate(resolved, LocalDate.of(2026, 8, 25));
    }

    private static StrategyGenerationResult.Candidate candidate(String quantity) {
        return new StrategyGenerationResult.Candidate(
                "CAND-1",
                List.of(StrategyType.PRICE_DISCOUNT),
                LocalDate.of(2026, 8, 25),
                LocalDate.of(2026, 8, 31),
                List.of(new StrategyGenerationResult.Action(
                        StrategyType.PRICE_DISCOUNT,
                        501L,
                        10L,
                        501L,
                        10L,
                        new BigDecimal(quantity),
                        BigDecimal.ZERO,
                        new BigDecimal("8500"),
                        new BigDecimal("0.15"),
                        List.of(new StrategyGenerationResult.LotAllocation(
                                1L, 1001L, new BigDecimal(quantity), 1
                        ))
                )),
                List.of(),
                new StrategyGenerationResult.Preference(1, 1, 100),
                new BigDecimal(quantity)
        );
    }

    private static StrategyGenerationResult.Candidate candidateWithoutSalesPoint(
            String quantity
    ) {
        return new StrategyGenerationResult.Candidate(
                "CAND-1",
                List.of(StrategyType.PRICE_DISCOUNT),
                LocalDate.of(2026, 8, 25),
                LocalDate.of(2026, 8, 31),
                List.of(new StrategyGenerationResult.Action(
                        StrategyType.PRICE_DISCOUNT,
                        501L,
                        null,
                        null,
                        null,
                        new BigDecimal(quantity),
                        BigDecimal.ZERO,
                        new BigDecimal("8500"),
                        new BigDecimal("0.15"),
                        List.of(new StrategyGenerationResult.LotAllocation(
                                1L, 1001L, new BigDecimal(quantity), 1
                        ))
                )),
                List.of(),
                new StrategyGenerationResult.Preference(1, 1, 100),
                new BigDecimal(quantity)
        );
    }

    private static StrategyCalculationInventoryVO inventory(String available) {
        StrategyCalculationInventoryVO row = new StrategyCalculationInventoryVO();
        row.setInventoryBalanceId(1L);
        row.setSkuId(100L);
        row.setWarehouseId(501L);
        row.setStockSalesPointId(10L);
        row.setAllocatedSalesPointId(10L);
        row.setLotId(1001L);
        row.setOnHandQty(new BigDecimal(available));
        row.setReservedQty(BigDecimal.ZERO);
        row.setLotStatus("AVAILABLE");
        return row;
    }
}
