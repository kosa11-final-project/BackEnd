package com.stockit.backend.feature.strategy.approval;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Predicate;

import org.springframework.stereotype.Component;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.strategy.domain.StrategyType;
import com.stockit.backend.feature.strategy.result.StrategyGenerationResult;

/** 복합 액션에서 같은 재고를 중복 합산하지 않고 실제 적용 재고 수량을 계산한다. */
@Component
public class StrategyAppliedQuantityCalculator {

    public BigDecimal calculate(StrategyGenerationResult.Candidate candidate) {
        List<StrategyGenerationResult.Action> inventoryActions = selectInventoryLayer(
                candidate.actions()
        );
        BigDecimal quantity = inventoryActions.stream()
                .flatMap(action -> action.lotAllocations().stream())
                .map(StrategyGenerationResult.LotAllocation::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (quantity.signum() <= 0) {
            throw new AppException(
                    ErrorCode.AI_STRATEGY_SELECTION_CONFLICT,
                    "최종 전략의 적용 재고 수량을 계산할 수 없습니다."
            );
        }
        for (StrategyGenerationResult.Action action : inventoryActions) {
            BigDecimal allocated = action.lotAllocations().stream()
                    .map(StrategyGenerationResult.LotAllocation::quantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (allocated.compareTo(action.actionQuantity()) != 0) {
                throw new AppException(
                        ErrorCode.AI_STRATEGY_SELECTION_CONFLICT,
                        "최종 전략의 액션 수량과 LOT 배정 수량이 일치하지 않습니다."
                );
            }
        }
        return quantity;
    }

    private static List<StrategyGenerationResult.Action> selectInventoryLayer(
            List<StrategyGenerationResult.Action> actions
    ) {
        List<StrategyGenerationResult.Action> movement = filter(
                actions,
                action -> action.actionType() == StrategyType.REALLOCATION
                        || action.actionType() == StrategyType.RT_TRANSFER
        );
        if (!movement.isEmpty()) return movement;
        List<StrategyGenerationResult.Action> discount = filter(
                actions,
                action -> action.actionType() == StrategyType.PRICE_DISCOUNT
        );
        if (!discount.isEmpty()) return discount;
        return actions.stream()
                .filter(action -> !action.lotAllocations().isEmpty())
                .toList();
    }

    private static List<StrategyGenerationResult.Action> filter(
            List<StrategyGenerationResult.Action> actions,
            Predicate<StrategyGenerationResult.Action> predicate
    ) {
        return actions.stream()
                .filter(predicate)
                .filter(action -> !action.lotAllocations().isEmpty())
                .toList();
    }
}
