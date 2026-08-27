package com.stockit.backend.feature.strategy.calculation.candidate.calculator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.stockit.backend.feature.strategy.calculation.candidate.domain.CandidateExclusion;
import com.stockit.backend.feature.strategy.calculation.candidate.domain.CandidateExclusionReason;
import com.stockit.backend.feature.strategy.calculation.candidate.domain.CandidateGenerationResult;
import com.stockit.backend.feature.strategy.calculation.candidate.domain.MovementCandidatePlan;
import com.stockit.backend.feature.strategy.calculation.candidate.domain.StrategyCandidate;
import com.stockit.backend.feature.strategy.calculation.candidate.domain.StrategyCandidateIdGenerator;
import com.stockit.backend.feature.strategy.calculation.candidate.policy.MovementLotAllocationPolicy;
import com.stockit.backend.feature.strategy.calculation.candidate.policy.SourceInventoryCapacityPolicy;
import com.stockit.backend.feature.strategy.calculation.candidate.policy.TargetAdditionalDemandPolicy;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext.InventoryLot;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext.SalesPoint;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext.WarehouseRoute;
import com.stockit.backend.feature.strategy.calculation.engine.CalculationPrecisionPolicy;
import com.stockit.backend.feature.strategy.domain.StrategyType;

/**
 * 재할당과 실제 이동 후보에 공통으로 적용되는 수량·LOT·이동 경로 계산 팩토리
 */
@Component
public class InventoryMovementCandidateFactory {

    private static final int MAX_RT_DESTINATIONS_PER_TARGET = 3;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal ZERO_MONEY = BigDecimal.ZERO.setScale(
            CalculationPrecisionPolicy.MONEY_SCALE
    );

    private final SourceInventoryCapacityPolicy sourceCapacityPolicy;
    private final TargetAdditionalDemandPolicy targetDemandPolicy;
    private final MovementLotAllocationPolicy allocationPolicy;
    private final StrategyCandidateIdGenerator idGenerator;
    private final InventoryTransferCostCalculator transferCostCalculator;

    public InventoryMovementCandidateFactory(
            SourceInventoryCapacityPolicy sourceCapacityPolicy,
            TargetAdditionalDemandPolicy targetDemandPolicy,
            MovementLotAllocationPolicy allocationPolicy,
            StrategyCandidateIdGenerator idGenerator,
            InventoryTransferCostCalculator transferCostCalculator
    ) {
        this.sourceCapacityPolicy = sourceCapacityPolicy;
        this.targetDemandPolicy = targetDemandPolicy;
        this.allocationPolicy = allocationPolicy;
        this.idGenerator = idGenerator;
        this.transferCostCalculator = transferCostCalculator;
    }

    /** 사용자 조건에 맞는 판매처별 재할당 또는 실제 이동 후보를 생성한다 */
    public CandidateGenerationResult generate(
            StrategyCalculationContext context,
            StrategyType strategyType,
            int strategyPriority
    ) {
        if (strategyType != StrategyType.REALLOCATION
                && strategyType != StrategyType.RT_TRANSFER) {
            throw new IllegalArgumentException(
                    "Unsupported inventory movement strategy type: " + strategyType
            );
        }
        return generate(
                context,
                strategyType,
                strategyPriority,
                orderedTargetSalesPointIds(context),
                true
        );
    }

    /**
     * 대상 판매처 목록과 가격 정보 필수 여부를 지정해 이동 기반 후보를 생성한다
     *
     * <p>채널 확대는 아직 입점하지 않은 판매처도 물류 실행 가능성을 평가해야 하므로
     * 가격 정보 검증을 선택적으로 생략할 수 있다</p>
     */
    public CandidateGenerationResult generate(
            StrategyCalculationContext context,
            StrategyType strategyType,
            int strategyPriority,
            List<Long> orderedTargetIds,
            boolean requireCompleteTargetPrice
    ) {
        if (strategyType != StrategyType.REALLOCATION
                && strategyType != StrategyType.RT_TRANSFER) {
            throw new IllegalArgumentException(
                    "Unsupported inventory movement strategy type: " + strategyType
            );
        }
        List<StrategyCandidate> candidates = new ArrayList<>();
        Set<CandidateExclusion> exclusions = new LinkedHashSet<>();
        if (orderedTargetIds.isEmpty()) {
            return new CandidateGenerationResult(candidates, List.of());
        }
        // 모든 판매처 후보가 같은 전략 시작일을 사용하므로 재고 투영은 한 번만 수행
        SourceInventoryCapacityPolicy.Projection projection =
                sourceCapacityPolicy.projectAt(context, context.strategyStartDate());
        StrategyCalculationContext projectedContext = context.withInventory(
                projection.evaluationInventory(),
                projection.referenceInventory()
        );

        for (int index = 0; index < orderedTargetIds.size(); index++) {
            Long targetId = orderedTargetIds.get(index);
            TargetResult targetResult = generateForTarget(
                    projectedContext,
                    strategyType,
                    strategyPriority,
                    index + 1,
                    targetId,
                    requireCompleteTargetPrice
            );
            candidates.addAll(targetResult.candidates());
            exclusions.addAll(targetResult.exclusions());
        }
        return new CandidateGenerationResult(candidates, List.copyOf(exclusions));
    }

    private TargetResult generateForTarget(
            StrategyCalculationContext context,
            StrategyType strategyType,
            int strategyPriority,
            int targetPriority,
            Long targetId,
            boolean requireCompleteTargetPrice
    ) {
        if (Objects.equals(context.sourceSalesPointId(), targetId)) {
            return excluded(strategyType, targetId, CandidateExclusionReason.SAME_AS_SOURCE,
                    "Source sales point cannot also be the movement target");
        }
        SalesPoint target = context.salesPoints().get(targetId);
        if (target == null
                || (requireCompleteTargetPrice && !target.hasCompletePrice())) {
            return excluded(
                    strategyType,
                    targetId,
                    CandidateExclusionReason.TARGET_PRICE_INCOMPLETE,
                    "Target price or variable cost is incomplete"
            );
        }

        List<WarehouseSelection> selections = selectEligibleLots(
                context.evaluationInventory(),
                target,
                strategyType
        );
        Map<LocalDate, BigDecimal> unmetDemand = targetDemandPolicy.calculate(
                context,
                targetId
        );
        BigDecimal targetDemandTotal = sum(unmetDemand.values());
        if (targetDemandTotal.signum() == 0) {
            return excluded(
                    strategyType,
                    targetId,
                    CandidateExclusionReason.TARGET_ADDITIONAL_DEMAND_NOT_FOUND,
                    "Target has no additional sellable demand in the strategy period"
            );
        }

        List<StrategyCandidate> candidates = new ArrayList<>();
        Set<CandidateExclusion> exclusions = new LinkedHashSet<>();
        for (WarehouseSelection selection : selections) {
            if (selection.exclusionReason() != null) {
                exclusions.add(new CandidateExclusion(
                        strategyType,
                        targetId,
                        selection.exclusionReason(),
                        selection.exclusionMessage()
                ));
                continue;
            }
            TargetResult result = generateForDestination(
                    context,
                    strategyType,
                    strategyPriority,
                    targetPriority,
                    targetId,
                    selection,
                    unmetDemand,
                    targetDemandTotal
            );
            candidates.addAll(result.candidates());
            exclusions.addAll(result.exclusions());
        }
        return new TargetResult(candidates, List.copyOf(exclusions));
    }

    private TargetResult generateForDestination(
            StrategyCalculationContext context,
            StrategyType strategyType,
            int strategyPriority,
            int targetPriority,
            Long targetId,
            WarehouseSelection selection,
            Map<LocalDate, BigDecimal> unmetDemand,
            BigDecimal targetDemandTotal
    ) {
        SourceInventoryCapacityPolicy.Capacity sourceCapacity = sourceCapacityPolicy.resolve(
                context,
                selection.eligibleLots()
        );
        if (sourceCapacity.total().signum() == 0) {
            return excluded(
                    strategyType,
                    targetId,
                    CandidateExclusionReason.SOURCE_STOCK_INSUFFICIENT,
                    "No sellable source inventory is available to move"
            );
        }
        MovementCandidatePlan maximumPlan = allocationPolicy.plan(
                selection.eligibleLots(),
                sourceCapacity.byLocation(),
                unmetDemand,
                sourceCapacity.total().min(targetDemandTotal)
        );
        BigDecimal maxExecutableQuantity = CalculationPrecisionPolicy
                .executableQuantity(maximumPlan.plannedQuantity());
        if (maxExecutableQuantity.signum() == 0) {
            return excluded(
                    strategyType,
                    targetId,
                    CandidateExclusionReason.LOT_NOT_SELLABLE_IN_PERIOD,
                    "Selected LOT cannot be sold before expiry or sale stop date"
            );
        }

        List<StrategyCandidate> candidates = new ArrayList<>();
        List<CandidateExclusion> exclusions = new ArrayList<>();
        Set<BigDecimal> generatedQuantities = new LinkedHashSet<>();
        for (int percentage = 10; percentage <= 100; percentage += 10) {
            BigDecimal requested = CalculationPrecisionPolicy.executableQuantity(
                    maxExecutableQuantity
                            .multiply(BigDecimal.valueOf(percentage))
                            .divide(ONE_HUNDRED,
                                    CalculationPrecisionPolicy.QUANTITY_SCALE,
                                    RoundingMode.DOWN)
            );
            if (requested.signum() == 0 || !generatedQuantities.add(requested)) {
                continue;
            }
            MovementCandidatePlan tierPlan = allocationPolicy.plan(
                    selection.eligibleLots(),
                    sourceCapacity.byLocation(),
                    unmetDemand,
                    requested
            );
            if (tierPlan.plannedQuantity().compareTo(requested) != 0) {
                continue;
            }
            List<StrategyCandidate.Action> actions;
            try {
                actions = toActions(
                        context,
                        tierPlan,
                        strategyType,
                        targetId,
                        selection.destination()
                );
            } catch (InventoryTransferCostCalculationException exception) {
                exclusions.add(new CandidateExclusion(
                        strategyType,
                        targetId,
                        exception.getReason(),
                        exception.getMessage()
                ));
                break;
            }
            String candidateId = idGenerator.generate(
                    strategyType,
                    context.strategyStartDate(),
                    null,
                    actions
            );
            candidates.add(new StrategyCandidate(
                    candidateId,
                    List.of(strategyType),
                    context.strategyStartDate(),
                    null,
                    actions,
                    List.of(),
                    new StrategyCandidate.Preference(
                            strategyPriority,
                            targetPriority,
                            percentage
                    ),
                    new StrategyCandidate.MovementEvidence(
                            maxExecutableQuantity,
                            sourceCapacity.total(),
                            targetDemandTotal,
                            maxExecutableQuantity
                    )
            ));
        }
        return new TargetResult(candidates, exclusions);
    }

    private List<WarehouseSelection> selectEligibleLots(
            List<InventoryLot> evaluationInventory,
            SalesPoint target,
            StrategyType strategyType
    ) {
        if (strategyType == StrategyType.REALLOCATION
                && target.warehouseRoutes().isEmpty()) {
            return List.of(WarehouseSelection.excluded(
                    CandidateExclusionReason.TARGET_ROUTE_NOT_FOUND,
                    "Target sales point has no active warehouse route"
            ));
        }
        if (strategyType == StrategyType.REALLOCATION) {
            Set<Long> targetWarehouseIds = target.warehouseRoutes().stream()
                    .map(WarehouseRoute::warehouseId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            List<InventoryLot> eligible = evaluationInventory.stream()
                    .filter(lot -> lot.warehouseId() != null)
                    .filter(lot -> targetWarehouseIds.contains(lot.warehouseId()))
                    .toList();
            if (eligible.isEmpty()) {
                return List.of(WarehouseSelection.excluded(
                        CandidateExclusionReason.SHARED_WAREHOUSE_NOT_FOUND,
                        "Source inventory and target do not share a warehouse"
                ));
            }
            return List.of(new WarehouseSelection(eligible, null, null, null));
        }

        List<StrategyCandidate.Location> destinations = new ArrayList<>();
        destinations.add(new StrategyCandidate.Location(null, target.salesPointId()));
        target.warehouseRoutes().stream()
                .map(WarehouseRoute::warehouseId)
                .distinct()
                // 판매처 직접 이동 1개와 우선순위가 높은 담당 창고 2개까지만 평가한다.
                .limit(MAX_RT_DESTINATIONS_PER_TARGET - 1L)
                .forEach(warehouseId -> destinations.add(
                        new StrategyCandidate.Location(
                                warehouseId,
                                target.salesPointId()
                        )
                ));
        List<WarehouseSelection> selections = new ArrayList<>();
        for (StrategyCandidate.Location destination : destinations) {
            List<InventoryLot> eligible = evaluationInventory.stream()
                    .filter(lot -> !samePhysicalLocation(lot, destination))
                    .toList();
            if (eligible.isEmpty()) {
                selections.add(WarehouseSelection.excluded(
                        CandidateExclusionReason.PHYSICAL_TRANSFER_NOT_REQUIRED,
                        "All selected inventory already resides at destination "
                                + destination
                ));
            } else {
                selections.add(new WarehouseSelection(
                        eligible,
                        destination,
                        null,
                        null
                ));
            }
        }
        return List.copyOf(selections);
    }

    private List<StrategyCandidate.Action> toActions(
            StrategyCalculationContext context,
            MovementCandidatePlan plan,
            StrategyType strategyType,
            Long targetSalesPointId,
            StrategyCandidate.Location transferDestination
    ) {
        Map<ActionKey, List<MovementCandidatePlan.Allocation>> grouped = plan.allocations()
                .stream()
                .collect(Collectors.groupingBy(
                        allocation -> new ActionKey(
                                allocation.sourceWarehouseId(),
                                allocation.sourceSalesPointId()
                        ),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        List<StrategyCandidate.Action> actions = new ArrayList<>();
        for (Map.Entry<ActionKey, List<MovementCandidatePlan.Allocation>> entry
                : grouped.entrySet()) {
            List<StrategyCandidate.LotAllocation> lotAllocations = new ArrayList<>();
            int priority = 1;
            for (MovementCandidatePlan.Allocation allocation : entry.getValue()) {
                lotAllocations.add(new StrategyCandidate.LotAllocation(
                        allocation.inventoryBalanceId(),
                        allocation.lotId(),
                        allocation.quantity(),
                        priority++
                ));
            }
            BigDecimal actionQuantity = sum(entry.getValue().stream()
                    .map(MovementCandidatePlan.Allocation::quantity)
                    .toList());
            StrategyCandidate.Location source = new StrategyCandidate.Location(
                    entry.getKey().warehouseId(),
                    entry.getKey().salesPointId()
            );
            StrategyCandidate.Location target = strategyType == StrategyType.REALLOCATION
                    ? new StrategyCandidate.Location(
                            entry.getKey().warehouseId(),
                            targetSalesPointId
                    )
                    : transferDestination;
            StrategyCandidate.MovementCost movementCost =
                    strategyType == StrategyType.RT_TRANSFER
                            ? transferCostCalculator.calculate(
                                    context,
                                    source,
                                    target,
                                    actionQuantity,
                                    context.strategyStartDate()
                            )
                            : null;
            actions.add(new StrategyCandidate.Action(
                    strategyType,
                    source,
                    target,
                    actionQuantity,
                    movementCost == null ? ZERO_MONEY : movementCost.estimatedCost(),
                    null,
                    null,
                    lotAllocations,
                    movementCost
            ));
        }
        actions.sort(Comparator.comparing(
                action -> action.source().warehouseId(),
                Comparator.nullsLast(Comparator.naturalOrder())
        ));
        return actions;
    }

    private static List<Long> orderedTargetSalesPointIds(
            StrategyCalculationContext context
    ) {
        List<Long> requested = context.requestConstraints()
                .orderedCandidateSalesPointIds();
        if (!requested.isEmpty()) {
            return requested;
        }
        return context.salesPoints().keySet().stream().toList();
    }

    private static boolean samePhysicalLocation(
            InventoryLot lot,
            StrategyCandidate.Location location
    ) {
        if (location.warehouseId() != null) {
            return Objects.equals(lot.warehouseId(), location.warehouseId());
        }
        return lot.warehouseId() == null
                && Objects.equals(
                        lot.effectiveSalesPointId(),
                        location.salesPointId()
                );
    }

    private static TargetResult excluded(
            StrategyType type,
            Long targetId,
            CandidateExclusionReason reason,
            String message
    ) {
        return new TargetResult(
                List.of(),
                List.of(new CandidateExclusion(type, targetId, reason, message))
        );
    }

    private static BigDecimal sum(Iterable<BigDecimal> values) {
        BigDecimal result = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            result = result.add(value);
        }
        return quantity(result);
    }

    private static BigDecimal quantity(BigDecimal value) {
        return CalculationPrecisionPolicy.quantity(value);
    }

    private record WarehouseSelection(
            List<InventoryLot> eligibleLots,
            StrategyCandidate.Location destination,
            CandidateExclusionReason exclusionReason,
            String exclusionMessage
    ) {
        private static WarehouseSelection excluded(
                CandidateExclusionReason reason,
                String message
        ) {
            return new WarehouseSelection(List.of(), null, reason, message);
        }
    }

    private record TargetResult(
            List<StrategyCandidate> candidates,
            List<CandidateExclusion> exclusions
    ) {
    }

    private record ActionKey(Long warehouseId, Long salesPointId) {
    }
}
