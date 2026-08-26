package com.stockit.backend.feature.strategy.simulation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.strategy.calculation.candidate.domain.CandidateExclusion;
import com.stockit.backend.feature.strategy.calculation.candidate.domain.CandidateGenerationResult;
import com.stockit.backend.feature.strategy.calculation.candidate.domain.CandidateExclusionReason;
import com.stockit.backend.feature.strategy.calculation.candidate.domain.StrategyCandidate;
import com.stockit.backend.feature.strategy.calculation.candidate.calculator.InventoryTransferCostCalculator;
import com.stockit.backend.feature.strategy.calculation.candidate.policy.StrategyPeriodEligibilityPolicy;
import com.stockit.backend.feature.strategy.calculation.candidate.policy.StrategyPeriodEligibilityPolicy.PeriodConstraints;
import com.stockit.backend.feature.strategy.calculation.candidate.service.StrategyCandidateGenerationService;
import com.stockit.backend.feature.strategy.calculation.domain.SimulationDetailLevel;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext.Price;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext.SalesPoint;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCandidateSimulation;
import com.stockit.backend.feature.strategy.calculation.engine.CalculationPrecisionPolicy;
import com.stockit.backend.feature.strategy.calculation.engine.CandidateSimulationException;
import com.stockit.backend.feature.strategy.calculation.engine.StrategyCandidateSimulationEngine;
import com.stockit.backend.feature.strategy.calculation.policy.SalesPointDiscountPolicy;
import com.stockit.backend.feature.strategy.calculation.policy.SalesPointDiscountPolicy.DiscountPolicy;
import com.stockit.backend.feature.strategy.domain.StrategyType;
import com.stockit.backend.feature.strategy.dto.response.AdjustedAiStrategySimulationResponse;
import com.stockit.backend.feature.strategy.dto.response.AiStrategyChartRangeResponse;
import com.stockit.backend.feature.strategy.dto.response.AiStrategyPeriodConstraintsResponse;
import com.stockit.backend.feature.strategy.result.InvalidStrategyResultException;
import com.stockit.backend.feature.strategy.result.StrategyGenerationResult;
import com.stockit.backend.feature.strategy.result.StrategyResultStore;
import com.stockit.backend.feature.strategy.result.StrategyResultStoreException;
import com.stockit.backend.feature.strategy.service.StrategyCaseLifecycleGuard;
import com.stockit.backend.feature.strategy.service.StrategyDateTimeProvider;

/** 생성 당시 스냅샷에서 수량·할인율·기간만 바꾸어 서버 계산을 재실행한다. */
@Service
public class StrategyAdjustmentSimulationServiceImpl
        implements StrategyAdjustmentSimulationService {

    private static final BigDecimal DISCOUNT_STEP = new BigDecimal("0.0500");

    private final StrategyResultStore resultStore;
    private final StrategySimulationContextStore contextStore;
    private final StrategyCandidateGenerationService candidateGenerationService;
    private final StrategyCandidateSimulationEngine simulationEngine;
    private final SalesPointDiscountPolicy salesPointDiscountPolicy;
    private final StrategyPeriodEligibilityPolicy periodEligibilityPolicy;
    private final StrategyCaseLifecycleGuard lifecycleGuard;
    private final StrategyDateTimeProvider dateTimeProvider;
    private final InventoryTransferCostCalculator transferCostCalculator;

    public StrategyAdjustmentSimulationServiceImpl(
            StrategyResultStore resultStore,
            StrategySimulationContextStore contextStore,
            StrategyCandidateGenerationService candidateGenerationService,
            StrategyCandidateSimulationEngine simulationEngine,
            SalesPointDiscountPolicy salesPointDiscountPolicy,
            StrategyPeriodEligibilityPolicy periodEligibilityPolicy,
            StrategyCaseLifecycleGuard lifecycleGuard,
            StrategyDateTimeProvider dateTimeProvider,
            InventoryTransferCostCalculator transferCostCalculator
    ) {
        this.resultStore = resultStore;
        this.contextStore = contextStore;
        this.candidateGenerationService = candidateGenerationService;
        this.simulationEngine = simulationEngine;
        this.salesPointDiscountPolicy = salesPointDiscountPolicy;
        this.periodEligibilityPolicy = periodEligibilityPolicy;
        this.lifecycleGuard = lifecycleGuard;
        this.dateTimeProvider = dateTimeProvider;
        this.transferCostCalculator = transferCostCalculator;
    }

    @Override
    public AdjustedAiStrategySimulationResponse simulate(
            Long strategyCaseId,
            String candidateId,
            AdjustStrategySimulationCommand command
    ) {
        ResolvedStrategyAdjustment resolved = resolve(
                strategyCaseId,
                candidateId,
                command,
                null
        );
        return response(
                strategyCaseId,
                resolved.originalOption().candidate(),
                resolved.adjustedContext(),
                resolved.adjustedCandidate(),
                resolved.command(),
                resolved.periodConstraints(),
                resolved.simulation()
        );
    }

    @Override
    public ResolvedStrategyAdjustment resolve(
            Long strategyCaseId,
            String candidateId,
            AdjustStrategySimulationCommand command,
            LocalDate businessDate
    ) {
        return resolveInternal(
                strategyCaseId, candidateId, command, businessDate, false
        );
    }

    @Override
    public ResolvedStrategyAdjustment resolveForSelection(
            Long strategyCaseId,
            String candidateId,
            AdjustStrategySimulationCommand command,
            LocalDate businessDate
    ) {
        return resolveInternal(
                strategyCaseId, candidateId, command, businessDate, true
        );
    }

    private ResolvedStrategyAdjustment resolveInternal(
            Long strategyCaseId,
            String candidateId,
            AdjustStrategySimulationCommand command,
            LocalDate businessDate,
            boolean selectionRequest
    ) {
        validateIdentity(strategyCaseId, candidateId);
        if (selectionRequest) {
            lifecycleGuard.requireSelectable(strategyCaseId);
        } else {
            lifecycleGuard.requireAdjustable(strategyCaseId);
        }
        StrategyGenerationResult result = loadResult(strategyCaseId);
        StrategyCalculationContext originalContext = loadContext(strategyCaseId);
        StrategyGenerationResult.Option option = result.options().stream()
                .filter(value -> candidateId.equals(value.candidate().candidateId()))
                .findFirst()
                .orElseThrow(() -> new AppException(
                        ErrorCode.AI_STRATEGY_CANDIDATE_NOT_FOUND
                ));
        LocalDate effectiveBusinessDate = businessDate == null
                ? dateTimeProvider.now().toLocalDate()
                : businessDate;
        validateCommand(
                originalContext,
                option.candidate(),
                command,
                effectiveBusinessDate
        );

        StrategyCalculationContext adjustedContext = adjustedContext(
                originalContext,
                option.candidate(),
                command
        );
        StrategyCandidate baseCandidate = selectBaseCandidate(
                candidateGenerationService.generate(adjustedContext),
                option.candidate(),
                command
        );
        StrategyCandidate adjustedCandidate = resizeAndApplyDiscount(
                adjustedContext,
                option.candidate(),
                baseCandidate,
                command
        );
        List<Long> allocatedInventoryBalanceIds = allocatedInventoryBalanceIds(
                adjustedCandidate
        );
        periodEligibilityPolicy.validateAllocatedPeriod(
                adjustedContext,
                command.endDate(),
                allocatedInventoryBalanceIds
        );
        PeriodConstraints periodConstraints = periodEligibilityPolicy.constraints(
                adjustedContext,
                command.startDate(),
                command.endDate(),
                allocatedInventoryBalanceIds,
                effectiveBusinessDate
        );
        try {
            StrategyCandidateSimulation simulation = simulationEngine.simulate(
                    adjustedContext,
                    adjustedCandidate,
                    result.baselineSimulation(),
                    SimulationDetailLevel.WITH_DAILY_SERIES
            );
            return new ResolvedStrategyAdjustment(
                    result,
                    option,
                    adjustedContext,
                    adjustedCandidate,
                    periodConstraints,
                    simulation,
                    command
            );
        } catch (CandidateSimulationException exception) {
            throw new AppException(
                    ErrorCode.AI_STRATEGY_SIMULATION_INVALID,
                    exception.getMessage()
            );
        }
    }

    private StrategyGenerationResult loadResult(Long strategyCaseId) {
        try {
            return resultStore.find(strategyCaseId).orElseThrow(() ->
                    new AppException(ErrorCode.AI_STRATEGY_RESULT_EXPIRED));
        } catch (InvalidStrategyResultException exception) {
            throw new AppException(ErrorCode.AI_STRATEGY_RESULT_EXPIRED);
        } catch (StrategyResultStoreException exception) {
            throw exception;
        }
    }

    private StrategyCalculationContext loadContext(Long strategyCaseId) {
        try {
            return contextStore.find(strategyCaseId).orElseThrow(() ->
                    new AppException(ErrorCode.AI_STRATEGY_RESULT_EXPIRED));
        } catch (InvalidStrategyResultException exception) {
            throw new AppException(ErrorCode.AI_STRATEGY_RESULT_EXPIRED);
        } catch (StrategyResultStoreException exception) {
            throw exception;
        }
    }

    private static void validateIdentity(Long strategyCaseId, String candidateId) {
        if (strategyCaseId == null || strategyCaseId <= 0
                || candidateId == null || candidateId.isBlank()) {
            throw new AppException(ErrorCode.AI_STRATEGY_CANDIDATE_NOT_FOUND);
        }
    }

    private void validateCommand(
            StrategyCalculationContext context,
            StrategyGenerationResult.Candidate template,
            AdjustStrategySimulationCommand command,
            LocalDate businessDate
    ) {
        if (command == null || command.actionQuantity() == null
                || command.actionQuantity().signum() <= 0
                || command.actionQuantity().remainder(BigDecimal.ONE).signum() != 0
                || command.startDate() == null || command.endDate() == null
                || command.startDate().isAfter(command.endDate())) {
            throw new AppException(
                    ErrorCode.AI_STRATEGY_SIMULATION_INVALID,
                    "적용 수량은 1개 단위의 양수여야 하며 시작일은 종료일보다 늦을 수 없습니다."
            );
        }
        periodEligibilityPolicy.validateRequestedPeriod(
                context,
                command.startDate(),
                command.endDate(),
                businessDate
        );
        boolean discount = template.strategyTypes().contains(
                StrategyType.PRICE_DISCOUNT
        );
        if (discount != (command.discountRate() != null)) {
            throw new AppException(
                    ErrorCode.AI_STRATEGY_SIMULATION_INVALID,
                    discount
                            ? "할인 전략에는 할인율이 필요합니다."
                            : "할인 액션이 없는 전략에는 할인율을 지정할 수 없습니다."
            );
        }
        if (discount && (command.discountRate().signum() <= 0
                || command.discountRate().remainder(DISCOUNT_STEP).signum() != 0)) {
            throw new AppException(
                    ErrorCode.AI_STRATEGY_SIMULATION_INVALID,
                    "할인율은 5% 단위로 입력해야 합니다."
            );
        }
    }

    private static StrategyCalculationContext adjustedContext(
            StrategyCalculationContext context,
            StrategyGenerationResult.Candidate template,
            AdjustStrategySimulationCommand command
    ) {
        return new StrategyCalculationContext(
                context.strategyCaseId(),
                context.sourceSalesPointId(),
                context.calculatedAt(),
                context.forecastStartDate(),
                context.forecastEndDate(),
                context.sku(),
                context.unitCost(),
                new StrategyCalculationContext.RequestConstraints(
                        targetSalesPointIds(template),
                        List.of(generationType(template.strategyTypes())),
                        command.startDate(),
                        command.endDate()
                ),
                context.evaluationInventory(),
                context.referenceInventory(),
                context.inventoryPolicies(),
                context.salesPoints(),
                context.forecastMetadata(),
                context.transferRoutes(),
                context.transferCostPolicies()
        );
    }

    private static StrategyType generationType(List<StrategyType> types) {
        if (types.contains(StrategyType.CHANNEL_EXPANSION)) {
            return StrategyType.CHANNEL_EXPANSION;
        }
        if (types.contains(StrategyType.PRICE_DISCOUNT)
                && types.size() == 1) {
            return StrategyType.PRICE_DISCOUNT;
        }
        return types.stream()
                .filter(type -> type == StrategyType.REALLOCATION
                        || type == StrategyType.RT_TRANSFER)
                .findFirst()
                .orElseThrow(() -> new AppException(
                        ErrorCode.AI_STRATEGY_UNSUPPORTED_TYPE
                ));
    }

    private StrategyCandidate selectBaseCandidate(
            CandidateGenerationResult generated,
            StrategyGenerationResult.Candidate template,
            AdjustStrategySimulationCommand command
    ) {
        StrategyType generationType = generationType(template.strategyTypes());
        Set<Long> targets = new LinkedHashSet<>(targetSalesPointIds(template));
        List<StrategyCandidate> structurallyMatching = generated.candidates().stream()
                .filter(candidate -> candidate.strategyTypes().contains(generationType))
                .filter(candidate -> movementTargets(candidate).equals(targets))
                .filter(candidate -> candidate.startDate().equals(command.startDate()))
                .filter(candidate -> standaloneMovement(candidate)
                        || Objects.equals(candidate.endDate(), command.endDate()))
                .toList();
        StrategyCandidate selected = structurallyMatching.stream()
                .filter(candidate -> generationType != StrategyType.PRICE_DISCOUNT
                        || candidate.actions().stream()
                        .filter(action -> action.actionType()
                                == StrategyType.PRICE_DISCOUNT)
                        .allMatch(action -> action.discountRate().compareTo(
                                command.discountRate()) == 0))
                .max(Comparator.comparing(
                        StrategyAdjustmentSimulationServiceImpl::allocatedQuantity
                ))
                .filter(candidate -> allocatedQuantity(candidate).compareTo(
                        command.actionQuantity()) >= 0)
                .orElse(null);
        if (selected != null) return selected;
        boolean sellableEndExceeded = structurallyMatching.isEmpty()
                && generated.exclusions().stream()
                .anyMatch(exclusion -> matchesSellablePeriodExclusion(
                        exclusion, generationType, template
                ));
        if (sellableEndExceeded) {
            throw new AppException(
                    ErrorCode.AI_STRATEGY_SELLABLE_END_EXCEEDED,
                    "조정 기간 안에 판매 가능한 LOT를 배정할 수 없습니다."
            );
        }
        throw new AppException(
                ErrorCode.AI_STRATEGY_SIMULATION_INVALID,
                "조정 기간의 재고·수요 조건으로 요청 수량을 실행할 수 없습니다."
        );
    }

    private static boolean matchesSellablePeriodExclusion(
            CandidateExclusion exclusion,
            StrategyType generationType,
            StrategyGenerationResult.Candidate template
    ) {
        return exclusion.reason()
                == CandidateExclusionReason.LOT_NOT_SELLABLE_IN_PERIOD
                && exclusion.strategyType() == generationType
                && matchesExclusionTarget(
                        exclusion.targetSalesPointId(), template
                );
    }

    private static boolean matchesExclusionTarget(
            Long excludedTargetSalesPointId,
            StrategyGenerationResult.Candidate template
    ) {
        Set<Long> selectedTargets = template.actions().stream()
                .map(StrategyGenerationResult.Action::targetSalesPointId)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
        return excludedTargetSalesPointId == null
                ? selectedTargets.isEmpty()
                : selectedTargets.contains(excludedTargetSalesPointId);
    }

    private StrategyCandidate resizeAndApplyDiscount(
            StrategyCalculationContext context,
            StrategyGenerationResult.Candidate template,
            StrategyCandidate base,
            AdjustStrategySimulationCommand command
    ) {
        List<StrategyCandidate.Action> resized = resizeActions(
                context,
                base.actions(),
                command.actionQuantity(),
                command.startDate()
        );
        if (template.strategyTypes().contains(StrategyType.PRICE_DISCOUNT)
                && !base.strategyTypes().contains(StrategyType.PRICE_DISCOUNT)) {
            resized = addDiscountActions(
                    context,
                    resized,
                    command.discountRate()
            );
        }
        LocalDate candidateEnd = standaloneMovementTypes(template.strategyTypes())
                ? null
                : command.endDate();
        return new StrategyCandidate(
                template.candidateId(),
                template.strategyTypes(),
                command.startDate(),
                candidateEnd,
                resized,
                base.assumptions(),
                base.preference(),
                base.evidence()
        );
    }

    private List<StrategyCandidate.Action> resizeActions(
            StrategyCalculationContext context,
            List<StrategyCandidate.Action> actions,
            BigDecimal requestedQuantity,
            LocalDate startDate
    ) {
        BigDecimal remaining = CalculationPrecisionPolicy.executableQuantity(
                requestedQuantity
        );
        List<StrategyCandidate.Action> result = new ArrayList<>();
        for (StrategyCandidate.Action action : actions) {
            if (action.lotAllocations().isEmpty()) {
                result.add(new StrategyCandidate.Action(
                        action.actionType(), action.source(), action.target(),
                        requestedQuantity, action.estimatedActionCost(),
                        action.strategyPrice(), action.discountRate(), List.of()
                ));
                continue;
            }
            if (remaining.signum() == 0) continue;
            List<StrategyCandidate.LotAllocation> allocations = new ArrayList<>();
            int priority = 1;
            for (StrategyCandidate.LotAllocation allocation
                    : action.lotAllocations()) {
                if (remaining.signum() == 0) break;
                BigDecimal quantity = allocation.quantity().min(remaining);
                allocations.add(new StrategyCandidate.LotAllocation(
                        allocation.inventoryBalanceId(), allocation.lotId(),
                        quantity, priority++
                ));
                remaining = CalculationPrecisionPolicy.executableQuantity(
                        remaining.subtract(quantity)
                );
            }
            BigDecimal actionQuantity = allocations.stream()
                    .map(StrategyCandidate.LotAllocation::quantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            StrategyCandidate.MovementCost movementCost =
                    action.actionType() == StrategyType.RT_TRANSFER
                            ? transferCostCalculator.calculate(
                                    context,
                                    action.source(),
                                    action.target(),
                                    actionQuantity,
                                    startDate
                            )
                            : null;
            result.add(new StrategyCandidate.Action(
                    action.actionType(), action.source(), action.target(),
                    actionQuantity,
                    movementCost == null
                            ? action.estimatedActionCost()
                            : movementCost.estimatedCost(),
                    action.strategyPrice(), action.discountRate(), allocations,
                    movementCost
            ));
        }
        if (remaining.signum() != 0) {
            throw new AppException(
                    ErrorCode.AI_STRATEGY_SIMULATION_INVALID,
                    "요청 수량을 LOT에 배분할 수 없습니다."
            );
        }
        return List.copyOf(result);
    }

    private List<StrategyCandidate.Action> addDiscountActions(
            StrategyCalculationContext context,
            List<StrategyCandidate.Action> actions,
            BigDecimal discountRate
    ) {
        List<StrategyCandidate.Action> result = new ArrayList<>(actions);
        for (StrategyCandidate.Action movement : actions) {
            if (movement.lotAllocations().isEmpty()) continue;
            Long targetId = movement.target().salesPointId();
            SalesPoint target = context.salesPoints().get(targetId);
            Price price = commercialPrice(context, target);
            DiscountPolicy policy = salesPointDiscountPolicy.resolve(target);
            if (discountRate.compareTo(policy.maximumDiscountRate()) > 0) {
                throw new AppException(
                        ErrorCode.AI_STRATEGY_SIMULATION_INVALID,
                        "판매처 할인 상한을 초과했습니다."
                );
            }
            BigDecimal strategyPrice = CalculationPrecisionPolicy.money(
                    price.actualPrice().multiply(BigDecimal.ONE.subtract(discountRate))
            );
            if (price.minimumSellingPrice() != null
                    && strategyPrice.compareTo(price.minimumSellingPrice()) < 0) {
                throw new AppException(
                        ErrorCode.AI_STRATEGY_SIMULATION_INVALID,
                        "전략 판매가는 최저 판매가보다 낮을 수 없습니다."
                );
            }
            result.add(new StrategyCandidate.Action(
                    StrategyType.PRICE_DISCOUNT,
                    movement.target(),
                    movement.target(),
                    movement.actionQuantity(),
                    BigDecimal.ZERO,
                    strategyPrice,
                    discountRate,
                    movement.lotAllocations()
            ));
        }
        return List.copyOf(result);
    }

    private static Price commercialPrice(
            StrategyCalculationContext context,
            SalesPoint target
    ) {
        if (target != null && target.price() != null) return target.price();
        SalesPoint source = context.salesPoints().get(context.sourceSalesPointId());
        if (source == null || source.price() == null) {
            throw new AppException(
                    ErrorCode.AI_STRATEGY_SIMULATION_INVALID,
                    "할인 계산에 필요한 판매가가 없습니다."
            );
        }
        return source.price();
    }

    private AdjustedAiStrategySimulationResponse response(
            Long strategyCaseId,
            StrategyGenerationResult.Candidate template,
            StrategyCalculationContext context,
            StrategyCandidate adjusted,
            AdjustStrategySimulationCommand command,
            PeriodConstraints periodConstraints,
            StrategyCandidateSimulation simulation
    ) {
        StrategyCandidate.Action discountAction = adjusted.actions().stream()
                .filter(action -> action.actionType() == StrategyType.PRICE_DISCOUNT)
                .findFirst()
                .orElse(null);
        SalesPoint target = discountAction == null
                ? null
                : context.salesPoints().get(discountAction.target().salesPointId());
        DiscountPolicy discountPolicy = discountAction == null
                ? null
                : salesPointDiscountPolicy.resolve(target);
        return new AdjustedAiStrategySimulationResponse(
                strategyCaseId,
                template.candidateId(),
                new AdjustedAiStrategySimulationResponse.AdjustedConditions(
                        command.actionQuantity(),
                        command.discountRate(),
                        discountAction == null ? null : discountAction.strategyPrice(),
                        command.startDate(),
                        command.endDate(),
                        adjusted.evidence().maxExecutableQty(),
                        discountPolicy == null ? null : discountPolicy.group(),
                        discountPolicy == null
                                ? null
                                : discountPolicy.maximumDiscountRate()
                ),
                AiStrategyPeriodConstraintsResponse.from(periodConstraints),
                new AiStrategyChartRangeResponse(
                        command.startDate(),
                        command.endDate()
                ),
                simulation
        );
    }

    private static List<Long> targetSalesPointIds(
            StrategyGenerationResult.Candidate candidate
    ) {
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        candidate.actions().stream()
                .filter(action -> action.actionType() == StrategyType.REALLOCATION
                        || action.actionType() == StrategyType.RT_TRANSFER)
                .map(StrategyGenerationResult.Action::targetSalesPointId)
                .filter(Objects::nonNull)
                .forEach(result::add);
        return List.copyOf(result);
    }

    private static Set<Long> movementTargets(StrategyCandidate candidate) {
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        candidate.actions().stream()
                .filter(action -> action.actionType() == StrategyType.REALLOCATION
                        || action.actionType() == StrategyType.RT_TRANSFER)
                .map(action -> action.target().salesPointId())
                .filter(Objects::nonNull)
                .forEach(result::add);
        return Set.copyOf(result);
    }

    private static BigDecimal allocatedQuantity(StrategyCandidate candidate) {
        return candidate.actions().stream()
                .filter(action -> !action.lotAllocations().isEmpty())
                .map(StrategyCandidate.Action::actionQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static List<Long> allocatedInventoryBalanceIds(
            StrategyCandidate candidate
    ) {
        return candidate.actions().stream()
                .flatMap(action -> action.lotAllocations().stream())
                .map(StrategyCandidate.LotAllocation::inventoryBalanceId)
                .distinct()
                .toList();
    }

    private static boolean standaloneMovement(StrategyCandidate candidate) {
        return standaloneMovementTypes(candidate.strategyTypes());
    }

    private static boolean standaloneMovementTypes(List<StrategyType> types) {
        return types.size() == 1
                && (types.get(0) == StrategyType.REALLOCATION
                || types.get(0) == StrategyType.RT_TRANSFER);
    }
}
