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
import com.stockit.backend.feature.strategy.calculation.vo.StrategyCalculationPolicyVO;
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
                validator(mapper);

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
        StrategyCalculationInventoryVO current = unassignedInventory("10");
        current.setReservedQty(new BigDecimal("4"));
        StrategyCalculationCostVO cost = new StrategyCalculationCostVO();
        cost.setUnitCost(new BigDecimal("5000"));
        when(resolved.option()).thenReturn(option);
        when(resolved.calculationContext()).thenReturn(context);
        when(option.candidate()).thenReturn(candidate);
        when(context.sku()).thenReturn(sku);
        when(context.sourceSalesPointId()).thenReturn(null);
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
                validator(mapper);

        validator.validate(resolved, LocalDate.of(2026, 8, 25));
    }

    @Test
    void allowsSelectionOfAllAvailableInventoryBelowSafetyStock() {
        StrategyCalculationInputMapper mapper = mock(
                StrategyCalculationInputMapper.class
        );
        ResolvedStrategySelection resolved = executableSelection("19");
        StrategyCalculationInventoryVO current = unassignedInventory("19");
        when(mapper.selectInventory(100L)).thenReturn(List.of(current));
        when(mapper.selectEffectivePolicies(
                100L, LocalDate.of(2026, 8, 25)
        )).thenReturn(List.of(policy(2L, 501L, "20")));
        when(mapper.selectEffectiveCosts(
                100L, LocalDate.of(2026, 8, 25)
        )).thenReturn(List.of(cost()));

        validator(mapper).validate(resolved, LocalDate.of(2026, 8, 25));
    }

    @Test
    void rejectsExpiredSelectedLotEvenWhenOtherSellableInventoryIsSufficient() {
        StrategyCalculationInputMapper mapper = mock(
                StrategyCalculationInputMapper.class
        );
        ResolvedStrategySelection resolved = executableSelection("10");
        StrategyCalculationInventoryVO expired = unassignedInventory("10");
        expired.setExpiryDate(LocalDate.of(2026, 8, 24));
        StrategyCalculationInventoryVO sellable = unassignedInventory("10");
        sellable.setInventoryBalanceId(2L);
        sellable.setLotId(1002L);
        when(mapper.selectInventory(100L)).thenReturn(List.of(expired, sellable));

        assertThatThrownBy(() -> validator(mapper).validate(
                resolved, LocalDate.of(2026, 8, 25)
        )).isInstanceOfSatisfying(
                AppException.class,
                exception -> assertThat(exception.getMessage()).isEqualTo(
                        "최종 선택에 포함된 LOT가 현재 판매 가능한 상태가 아닙니다."
                )
        );
    }

    private static StrategySelectionExecutabilityValidator validator(
            StrategyCalculationInputMapper mapper
    ) {
        return new StrategySelectionExecutabilityValidator(
                mapper,
                mock(StrategyTransferInputFreshnessValidator.class)
        );
    }

    private static ResolvedStrategySelection executableSelection(String quantity) {
        ResolvedStrategySelection resolved = mock(ResolvedStrategySelection.class);
        StrategyCalculationContext context = mock(StrategyCalculationContext.class);
        StrategyCalculationContext.Sku sku = mock(StrategyCalculationContext.Sku.class);
        StrategyGenerationResult.Option option = mock(
                StrategyGenerationResult.Option.class
        );
        when(resolved.option()).thenReturn(option);
        when(resolved.calculationContext()).thenReturn(context);
        when(option.candidate()).thenReturn(candidateWithoutSalesPoint(quantity));
        when(context.sku()).thenReturn(sku);
        when(context.sourceSalesPointId()).thenReturn(null);
        when(context.unitCost()).thenReturn(new BigDecimal("5000"));
        when(sku.skuId()).thenReturn(100L);
        return resolved;
    }

    private static StrategyCalculationCostVO cost() {
        StrategyCalculationCostVO cost = new StrategyCalculationCostVO();
        cost.setUnitCost(new BigDecimal("5000"));
        return cost;
    }

    private static StrategyCalculationPolicyVO policy(
            Long policyId,
            Long warehouseId,
            String safetyQuantity
    ) {
        StrategyCalculationPolicyVO policy = new StrategyCalculationPolicyVO();
        policy.setInventoryPolicyId(policyId);
        policy.setWarehouseId(warehouseId);
        policy.setSafetyStockQty(new BigDecimal(safetyQuantity));
        return policy;
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

    private static StrategyCalculationInventoryVO unassignedInventory(
            String available
    ) {
        StrategyCalculationInventoryVO row = inventory(available);
        row.setStockSalesPointId(null);
        row.setAllocatedSalesPointId(null);
        return row;
    }
}
