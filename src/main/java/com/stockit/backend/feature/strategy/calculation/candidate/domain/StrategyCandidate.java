package com.stockit.backend.feature.strategy.calculation.candidate.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.stockit.backend.feature.strategy.domain.StrategyType;

/** 후속 시뮬레이션에 전달할 실행 가능한 전략 후보. */
public record StrategyCandidate(
        String candidateId,
        List<StrategyType> strategyTypes,
        LocalDate startDate,
        LocalDate endDate,
        List<Action> actions,
        List<CandidateAssumption> assumptions,
        Preference preference,
        Evidence evidence
) {
    public StrategyCandidate {
        if (candidateId == null || candidateId.isBlank()
                || strategyTypes == null || strategyTypes.isEmpty()
                || startDate == null || endDate == null || startDate.isAfter(endDate)
                || actions == null || actions.isEmpty()
                || preference == null || evidence == null) {
            throw new IllegalArgumentException("strategy candidate is invalid");
        }
        strategyTypes = List.copyOf(strategyTypes);
        actions = List.copyOf(actions);
        assumptions = List.copyOf(assumptions);
    }

    public record Action(
            StrategyType actionType,
            Location source,
            Location target,
            BigDecimal actionQuantity,
            BigDecimal estimatedActionCost,
            List<LotAllocation> lotAllocations
    ) {
        public Action {
            if (actionType == null || source == null || target == null
                    || actionQuantity == null || actionQuantity.signum() <= 0
                    || (estimatedActionCost != null && estimatedActionCost.signum() < 0)
                    || lotAllocations == null || lotAllocations.isEmpty()) {
                throw new IllegalArgumentException("candidate action is invalid");
            }
            lotAllocations = List.copyOf(lotAllocations);
            BigDecimal allocated = lotAllocations.stream()
                    .map(LotAllocation::quantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (allocated.compareTo(actionQuantity) != 0) {
                throw new IllegalArgumentException("action quantity must equal LOT allocations");
            }
        }
    }

    public record Location(Long warehouseId, Long salesPointId) {
        public Location {
            if (warehouseId == null && salesPointId == null) {
                throw new IllegalArgumentException("candidate location must not be empty");
            }
        }
    }

    public record LotAllocation(
            Long inventoryBalanceId,
            Long lotId,
            BigDecimal quantity,
            int priorityNo
    ) {
        public LotAllocation {
            if (inventoryBalanceId == null || inventoryBalanceId <= 0
                    || lotId == null || lotId <= 0
                    || quantity == null || quantity.signum() <= 0
                    || priorityNo <= 0) {
                throw new IllegalArgumentException("candidate LOT allocation is invalid");
            }
        }
    }

    /** 값이 작을수록 사용자가 먼저 선택한 조건이다. */
    public record Preference(
            int strategyPriority,
            int targetPriority,
            int quantityPercentage
    ) {
        public Preference {
            if (strategyPriority <= 0 || targetPriority <= 0
                    || quantityPercentage < 10 || quantityPercentage > 100
                    || quantityPercentage % 10 != 0) {
                throw new IllegalArgumentException("candidate preference is invalid");
            }
        }
    }

    /** 후보가 왜 이 수량까지만 실행 가능한지 설명하는 상한 근거. */
    public record Evidence(
            BigDecimal maxExecutableQty,
            BigDecimal sourceTransferableQty,
            BigDecimal targetAdditionalDemandQty,
            BigDecimal expirySellableQty
    ) {
        public Evidence {
            if (maxExecutableQty == null || maxExecutableQty.signum() <= 0
                    || sourceTransferableQty == null || sourceTransferableQty.signum() < 0
                    || targetAdditionalDemandQty == null
                    || targetAdditionalDemandQty.signum() < 0
                    || expirySellableQty == null || expirySellableQty.signum() < 0) {
                throw new IllegalArgumentException("candidate evidence is invalid");
            }
        }
    }
}
