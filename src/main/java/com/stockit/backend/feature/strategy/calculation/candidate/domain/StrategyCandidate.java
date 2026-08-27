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
                || startDate == null
                || (endDate != null && startDate.isAfter(endDate))
                || actions == null || actions.isEmpty()
                || preference == null || evidence == null) {
            throw new IllegalArgumentException("strategy candidate is invalid");
        }
        boolean standaloneMovement = strategyTypes.size() == 1
                && (strategyTypes.get(0) == StrategyType.REALLOCATION
                || strategyTypes.get(0) == StrategyType.RT_TRANSFER);
        if (endDate == null && !standaloneMovement) {
            throw new IllegalArgumentException(
                    "only standalone inventory movement may omit endDate"
            );
        }
        if (standaloneMovement && endDate != null) {
            throw new IllegalArgumentException(
                    "standalone inventory movement must omit endDate"
            );
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
            BigDecimal strategyPrice,
            BigDecimal discountRate,
            List<LotAllocation> lotAllocations,
            MovementCost movementCost
    ) {
        public Action(
                StrategyType actionType,
                Location source,
                Location target,
                BigDecimal actionQuantity,
                BigDecimal estimatedActionCost,
                BigDecimal strategyPrice,
                BigDecimal discountRate,
                List<LotAllocation> lotAllocations
        ) {
            this(
                    actionType,
                    source,
                    target,
                    actionQuantity,
                    estimatedActionCost,
                    strategyPrice,
                    discountRate,
                    lotAllocations,
                    null
            );
        }

        public Action(
                StrategyType actionType,
                Location source,
                Location target,
                BigDecimal actionQuantity,
                BigDecimal estimatedActionCost,
                List<LotAllocation> lotAllocations
        ) {
            this(
                    actionType,
                    source,
                    target,
                    actionQuantity,
                    estimatedActionCost,
                    null,
                    null,
                    lotAllocations,
                    null
            );
        }

        public Action {
            if (actionType == null || source == null || target == null
                    || actionQuantity == null || actionQuantity.signum() <= 0
                    || (estimatedActionCost != null && estimatedActionCost.signum() < 0)
                    || (strategyPrice != null && strategyPrice.signum() < 0)
                    || (discountRate != null
                    && (discountRate.signum() <= 0
                    || discountRate.compareTo(BigDecimal.ONE) >= 0))
                    || lotAllocations == null) {
                throw new IllegalArgumentException("candidate action is invalid");
            }
            lotAllocations = List.copyOf(lotAllocations);
            boolean inventoryAction = actionType == StrategyType.REALLOCATION
                    || actionType == StrategyType.RT_TRANSFER
                    || actionType == StrategyType.PRICE_DISCOUNT;
            if (inventoryAction && lotAllocations.isEmpty()) {
                throw new IllegalArgumentException(
                        "inventory candidate action requires LOT allocations"
                );
            }
            if (actionType == StrategyType.PRICE_DISCOUNT
                    && (strategyPrice == null || discountRate == null)) {
                throw new IllegalArgumentException(
                        "price discount action requires price and discount rate"
                );
            }
            if (movementCost != null) {
                if (actionType != StrategyType.RT_TRANSFER
                        || estimatedActionCost == null
                        || estimatedActionCost.compareTo(
                                movementCost.estimatedCost()
                        ) != 0) {
                    throw new IllegalArgumentException(
                            "movement cost must match RT_TRANSFER action cost"
                    );
                }
            }
            BigDecimal allocated = lotAllocations.stream()
                    .map(LotAllocation::quantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (!lotAllocations.isEmpty() && allocated.compareTo(actionQuantity) != 0) {
                throw new IllegalArgumentException("action quantity must equal LOT allocations");
            }
        }
    }

    /** RT_TRANSFER 이동비 계산 당시 사용한 경로·요율·중량 Snapshot. */
    public record MovementCost(
            Long transferRouteId,
            Long transferCostPolicyId,
            BigDecimal weightKg,
            BigDecimal distanceKm,
            BigDecimal costPerKgKm,
            BigDecimal estimatedCost
    ) {
        public MovementCost {
            if (transferRouteId == null || transferRouteId <= 0
                    || transferCostPolicyId == null || transferCostPolicyId <= 0
                    || weightKg == null || weightKg.signum() <= 0
                    || distanceKm == null || distanceKm.signum() <= 0
                    || costPerKgKm == null || costPerKgKm.signum() <= 0
                    || estimatedCost == null || estimatedCost.signum() < 0) {
                throw new IllegalArgumentException("movement cost snapshot is invalid");
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

    /** 전략별 계산 근거가 공통으로 노출해야 하는 실행 가능 수량. */
    public sealed interface Evidence permits MovementEvidence,
            DiscountEvidence, ChannelEvidence {

        BigDecimal maxExecutableQty();
    }

    /** 재할당·실제 이동 후보의 실행 가능량 근거. */
    public record MovementEvidence(
            BigDecimal maxExecutableQty,
            BigDecimal sourceTransferableQty,
            BigDecimal targetAdditionalDemandQty,
            BigDecimal expirySellableQty
    ) implements Evidence {
        public MovementEvidence {
            if (maxExecutableQty == null || maxExecutableQty.signum() <= 0
                    || sourceTransferableQty == null || sourceTransferableQty.signum() < 0
                    || targetAdditionalDemandQty == null
                    || targetAdditionalDemandQty.signum() < 0
                    || expirySellableQty == null || expirySellableQty.signum() < 0) {
                throw new IllegalArgumentException("candidate evidence is invalid");
            }
        }
    }

    /** 할인 후보의 가격 하한, 기본 수요와 LOT 판매 가능량 근거. */
    public record DiscountEvidence(
            BigDecimal maxExecutableQty,
            BigDecimal discountApplicableQty,
            BigDecimal baselineDemandQty,
            BigDecimal expirySellableQty,
            BigDecimal originalPrice,
            BigDecimal minimumSellingPrice
    ) implements Evidence {
        public DiscountEvidence {
            if (maxExecutableQty == null || maxExecutableQty.signum() <= 0
                    || discountApplicableQty == null
                    || discountApplicableQty.signum() < 0
                    || baselineDemandQty == null || baselineDemandQty.signum() < 0
                    || expirySellableQty == null || expirySellableQty.signum() < 0
                    || originalPrice == null || originalPrice.signum() < 0
                    || minimumSellingPrice == null
                    || minimumSellingPrice.signum() < 0) {
                throw new IllegalArgumentException("discount evidence is invalid");
            }
        }
    }

    /** 판매채널 후보와 이를 뒷받침하는 재고 이동 방식의 근거. */
    public record ChannelEvidence(
            BigDecimal maxExecutableQty,
            BigDecimal targetForecastQty,
            BigDecimal targetExistingInventoryQty,
            BigDecimal targetAdditionalDemandQty,
            StrategyType supportingMovementType,
            BigDecimal appliedSellingPrice,
            BigDecimal paymentFee,
            BigDecimal logisticsCost
    ) implements Evidence {
        public ChannelEvidence {
            if (maxExecutableQty == null || maxExecutableQty.signum() <= 0
                    || targetForecastQty == null || targetForecastQty.signum() < 0
                    || targetExistingInventoryQty == null
                    || targetExistingInventoryQty.signum() < 0
                    || targetAdditionalDemandQty == null
                    || targetAdditionalDemandQty.signum() < 0
                    || (supportingMovementType != StrategyType.REALLOCATION
                    && supportingMovementType != StrategyType.RT_TRANSFER)
                    || appliedSellingPrice == null || appliedSellingPrice.signum() < 0
                    || paymentFee == null || paymentFee.signum() < 0
                    || logisticsCost == null || logisticsCost.signum() < 0) {
                throw new IllegalArgumentException("channel evidence is invalid");
            }
        }
    }
}
