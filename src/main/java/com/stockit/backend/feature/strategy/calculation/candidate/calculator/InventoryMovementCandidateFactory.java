package com.stockit.backend.feature.strategy.calculation.candidate.calculator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.stockit.backend.feature.strategy.calculation.candidate.domain.CandidateAssumption;
import com.stockit.backend.feature.strategy.calculation.candidate.domain.CandidateExclusion;
import com.stockit.backend.feature.strategy.calculation.candidate.domain.CandidateExclusionReason;
import com.stockit.backend.feature.strategy.calculation.candidate.domain.CandidateGenerationResult;
import com.stockit.backend.feature.strategy.calculation.candidate.domain.MovementCandidatePlan;
import com.stockit.backend.feature.strategy.calculation.candidate.domain.StrategyCandidate;
import com.stockit.backend.feature.strategy.calculation.candidate.domain.StrategyCandidateIdGenerator;
import com.stockit.backend.feature.strategy.calculation.candidate.policy.MovementLotAllocationPolicy;
import com.stockit.backend.feature.strategy.calculation.candidate.policy.SafetyStockPolicyResolver;
import com.stockit.backend.feature.strategy.calculation.candidate.policy.TargetAdditionalDemandPolicy;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext.InventoryLot;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext.SalesPoint;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext.WarehouseRoute;
import com.stockit.backend.feature.strategy.calculation.engine.CalculationPrecisionPolicy;
import com.stockit.backend.feature.strategy.domain.StrategyType;

/** 재할당과 실제 이동이 공유하는 실행 가능량·LOT 배분·10% 수량 후보 규칙. */
@Component
public class InventoryMovementCandidateFactory {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal ZERO_MONEY = BigDecimal.ZERO.setScale(
            CalculationPrecisionPolicy.MONEY_SCALE
    );

    private final SafetyStockPolicyResolver safetyStockResolver;
    private final TargetAdditionalDemandPolicy targetDemandPolicy;
    private final MovementLotAllocationPolicy allocationPolicy;
    private final StrategyCandidateIdGenerator idGenerator;

    public InventoryMovementCandidateFactory(
            SafetyStockPolicyResolver safetyStockResolver,
            TargetAdditionalDemandPolicy targetDemandPolicy,
            MovementLotAllocationPolicy allocationPolicy,
            StrategyCandidateIdGenerator idGenerator
    ) {
        this.safetyStockResolver = safetyStockResolver;
        this.targetDemandPolicy = targetDemandPolicy;
        this.allocationPolicy = allocationPolicy;
        this.idGenerator = idGenerator;
    }

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
        List<StrategyCandidate> candidates = new ArrayList<>();
        List<CandidateExclusion> exclusions = new ArrayList<>();
        List<Long> targetIds = orderedTargetSalesPointIds(context);

        for (int index = 0; index < targetIds.size(); index++) {
            Long targetId = targetIds.get(index);
            TargetResult targetResult = generateForTarget(
                    context,
                    strategyType,
                    strategyPriority,
                    index + 1,
                    targetId
            );
            candidates.addAll(targetResult.candidates());
            exclusions.addAll(targetResult.exclusions());
        }
        return new CandidateGenerationResult(candidates, exclusions);
    }

    private TargetResult generateForTarget(
            StrategyCalculationContext context,
            StrategyType strategyType,
            int strategyPriority,
            int targetPriority,
            Long targetId
    ) {
        if (Objects.equals(context.sourceSalesPointId(), targetId)) {
            return excluded(strategyType, targetId, CandidateExclusionReason.SAME_AS_SOURCE,
                    "Source sales point cannot also be the movement target");
        }
        SalesPoint target = context.salesPoints().get(targetId);
        if (target == null || !target.hasCompletePrice()) {
            return excluded(
                    strategyType,
                    targetId,
                    CandidateExclusionReason.TARGET_PRICE_INCOMPLETE,
                    "Target price or variable cost is incomplete"
            );
        }

        WarehouseSelection selection = selectEligibleLots(
                context.evaluationInventory(),
                target,
                strategyType
        );
        if (selection.exclusionReason() != null) {
            return excluded(
                    strategyType,
                    targetId,
                    selection.exclusionReason(),
                    selection.exclusionMessage()
            );
        }

        SourceCapacity sourceCapacity = sourceCapacity(
                context,
                selection.eligibleLots()
        );
        if (sourceCapacity.total().signum() == 0) {
            CandidateExclusionReason reason = sourceCapacity.safetyStockBlocked()
                    ? CandidateExclusionReason.SOURCE_SAFETY_STOCK_VIOLATION
                    : CandidateExclusionReason.SOURCE_STOCK_INSUFFICIENT;
            return excluded(
                    strategyType,
                    targetId,
                    reason,
                    "No inventory can be moved while preserving source safety stock"
            );
        }

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

        MovementCandidatePlan maximumPlan = allocationPolicy.plan(
                selection.eligibleLots(),
                sourceCapacity.byWarehouse(),
                unmetDemand,
                sourceCapacity.total().min(targetDemandTotal)
        );
        if (maximumPlan.plannedQuantity().signum() == 0) {
            return excluded(
                    strategyType,
                    targetId,
                    CandidateExclusionReason.LOT_NOT_SELLABLE_IN_PERIOD,
                    "Selected LOT cannot be sold before expiry or sale stop date"
            );
        }

        List<CandidateAssumption> assumptions = assumptions(
                strategyType,
                sourceCapacity.safetyStockDefaulted()
        );
        List<StrategyCandidate> candidates = new ArrayList<>();
        Set<BigDecimal> generatedQuantities = new LinkedHashSet<>();
        for (int percentage = 10; percentage <= 100; percentage += 10) {
            BigDecimal requested = quantity(maximumPlan.plannedQuantity()
                    .multiply(BigDecimal.valueOf(percentage))
                    .divide(ONE_HUNDRED, CalculationPrecisionPolicy.QUANTITY_SCALE,
                            RoundingMode.DOWN));
            if (requested.signum() == 0 || !generatedQuantities.add(requested)) {
                continue;
            }
            MovementCandidatePlan tierPlan = allocationPolicy.plan(
                    selection.eligibleLots(),
                    sourceCapacity.byWarehouse(),
                    unmetDemand,
                    requested
            );
            if (tierPlan.plannedQuantity().compareTo(requested) != 0) {
                continue;
            }
            List<StrategyCandidate.Action> actions = toActions(
                    tierPlan,
                    strategyType,
                    targetId,
                    selection.targetWarehouseId()
            );
            String candidateId = idGenerator.generate(
                    strategyType,
                    context.forecastStartDate(),
                    context.forecastEndDate(),
                    actions
            );
            candidates.add(new StrategyCandidate(
                    candidateId,
                    List.of(strategyType),
                    context.forecastStartDate(),
                    context.forecastEndDate(),
                    actions,
                    assumptions,
                    new StrategyCandidate.Preference(
                            strategyPriority,
                            targetPriority,
                            percentage
                    ),
                    new StrategyCandidate.Evidence(
                            maximumPlan.plannedQuantity(),
                            sourceCapacity.total(),
                            targetDemandTotal,
                            maximumPlan.plannedQuantity()
                    )
            ));
        }
        return new TargetResult(candidates, List.of());
    }

    private WarehouseSelection selectEligibleLots(
            List<InventoryLot> evaluationInventory,
            SalesPoint target,
            StrategyType strategyType
    ) {
        if (target.warehouseRoutes().isEmpty()) {
            return WarehouseSelection.excluded(
                    CandidateExclusionReason.TARGET_ROUTE_NOT_FOUND,
                    "Target sales point has no active warehouse route"
            );
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
                return WarehouseSelection.excluded(
                        CandidateExclusionReason.SHARED_WAREHOUSE_NOT_FOUND,
                        "Source inventory and target do not share a warehouse"
                );
            }
            return new WarehouseSelection(eligible, null, null, null);
        }

        Long primaryTargetWarehouseId = target.warehouseRoutes().get(0).warehouseId();
        List<InventoryLot> eligible = evaluationInventory.stream()
                .filter(lot -> lot.warehouseId() != null)
                .filter(lot -> !Objects.equals(lot.warehouseId(), primaryTargetWarehouseId))
                .toList();
        if (eligible.isEmpty()) {
            return WarehouseSelection.excluded(
                    CandidateExclusionReason.PHYSICAL_TRANSFER_NOT_REQUIRED,
                    "All selected inventory already resides in the target warehouse"
            );
        }
        return new WarehouseSelection(eligible, primaryTargetWarehouseId, null, null);
    }

    private SourceCapacity sourceCapacity(
            StrategyCalculationContext context,
            List<InventoryLot> eligibleLots
    ) {
        Map<Long, List<InventoryLot>> selectedByWarehouse = eligibleLots.stream()
                .collect(Collectors.groupingBy(
                        InventoryLot::warehouseId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        Map<Long, BigDecimal> byWarehouse = new LinkedHashMap<>();
        boolean defaulted = false;
        boolean safetyBlocked = false;

        for (Map.Entry<Long, List<InventoryLot>> entry : selectedByWarehouse.entrySet()) {
            Long warehouseId = entry.getKey();
            BigDecimal selectedQuantity = sum(entry.getValue().stream()
                    .map(InventoryLot::availableQty)
                    .toList());
            BigDecimal sourceAvailable = sum(context.referenceInventory().stream()
                    .filter(lot -> Objects.equals(lot.warehouseId(), warehouseId))
                    .filter(lot -> matchesSource(lot, context.sourceSalesPointId()))
                    .filter(lot -> isSellableAt(lot, context.calculatedAt().toLocalDate()))
                    .map(InventoryLot::availableQty)
                    .toList());
            SafetyStockPolicyResolver.Resolution safety = safetyStockResolver.resolve(
                    context.inventoryPolicies(),
                    warehouseId,
                    context.sourceSalesPointId()
            );
            defaulted |= safety.defaultedToZero();
            BigDecimal afterSafety = quantity(sourceAvailable.subtract(
                    safety.safetyStockQty()
            ).max(BigDecimal.ZERO));
            safetyBlocked |= sourceAvailable.signum() > 0 && afterSafety.signum() == 0;
            byWarehouse.put(warehouseId, quantity(selectedQuantity.min(afterSafety)));
        }
        return new SourceCapacity(
                byWarehouse,
                sum(byWarehouse.values()),
                defaulted,
                safetyBlocked
        );
    }

    private static List<StrategyCandidate.Action> toActions(
            MovementCandidatePlan plan,
            StrategyType strategyType,
            Long targetSalesPointId,
            Long transferTargetWarehouseId
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
            Long destinationWarehouseId = strategyType == StrategyType.REALLOCATION
                    ? entry.getKey().warehouseId()
                    : transferTargetWarehouseId;
            actions.add(new StrategyCandidate.Action(
                    strategyType,
                    new StrategyCandidate.Location(
                            entry.getKey().warehouseId(),
                            entry.getKey().salesPointId()
                    ),
                    new StrategyCandidate.Location(
                            destinationWarehouseId,
                            targetSalesPointId
                    ),
                    actionQuantity,
                    strategyType == StrategyType.REALLOCATION ? ZERO_MONEY : null,
                    lotAllocations
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

    private static List<CandidateAssumption> assumptions(
            StrategyType strategyType,
            boolean safetyStockDefaulted
    ) {
        EnumSet<CandidateAssumption> assumptions = EnumSet.noneOf(
                CandidateAssumption.class
        );
        if (safetyStockDefaulted) {
            assumptions.add(CandidateAssumption.SAFETY_STOCK_DEFAULTED_TO_ZERO);
        }
        if (strategyType == StrategyType.RT_TRANSFER) {
            assumptions.add(CandidateAssumption.TRANSFER_COST_EXCLUDED);
        }
        return List.copyOf(assumptions);
    }

    private static boolean matchesSource(InventoryLot lot, Long sourceSalesPointId) {
        return sourceSalesPointId == null
                ? lot.isPublicUnassigned()
                : Objects.equals(lot.effectiveSalesPointId(), sourceSalesPointId);
    }

    private static boolean isSellableAt(InventoryLot lot, LocalDate date) {
        return "AVAILABLE".equals(lot.lotStatus())
                && (lot.expiryDate() == null || !date.isAfter(lot.expiryDate()))
                && (lot.saleStopDate() == null || date.isBefore(lot.saleStopDate()));
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
            Long targetWarehouseId,
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

    private record SourceCapacity(
            Map<Long, BigDecimal> byWarehouse,
            BigDecimal total,
            boolean safetyStockDefaulted,
            boolean safetyStockBlocked
    ) {
    }

    private record TargetResult(
            List<StrategyCandidate> candidates,
            List<CandidateExclusion> exclusions
    ) {
    }

    private record ActionKey(Long warehouseId, Long salesPointId) {
    }
}
