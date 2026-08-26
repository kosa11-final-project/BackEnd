package com.stockit.backend.feature.strategy.calculation.candidate.calculator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.stockit.backend.feature.strategy.calculation.candidate.domain.CandidateAssumption;
import com.stockit.backend.feature.strategy.calculation.candidate.domain.CandidateExclusion;
import com.stockit.backend.feature.strategy.calculation.candidate.domain.CandidateExclusionReason;
import com.stockit.backend.feature.strategy.calculation.candidate.domain.CandidateGenerationResult;
import com.stockit.backend.feature.strategy.calculation.candidate.domain.StrategyCandidate;
import com.stockit.backend.feature.strategy.calculation.candidate.domain.StrategyCandidateIdGenerator;
import com.stockit.backend.feature.strategy.calculation.candidate.domain.StrategyPeriodCandidate;
import com.stockit.backend.feature.strategy.calculation.candidate.policy.StrategyPeriodCandidatePolicy;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext.Price;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext.SalesPoint;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationException;
import com.stockit.backend.feature.strategy.calculation.engine.CalculationPrecisionPolicy;
import com.stockit.backend.feature.strategy.domain.StrategyType;

/** 채널 전략과 이를 실행하기 위한 재할당·RT 이동 후보를 결합한다. */
abstract class AbstractChannelStrategyCandidateCalculator
        implements StrategyCandidateCalculator {

    private static final BigDecimal ZERO_MONEY = BigDecimal.ZERO.setScale(
            CalculationPrecisionPolicy.MONEY_SCALE
    );

    private final InventoryMovementCandidateFactory movementFactory;
    private final StrategyCandidateIdGenerator idGenerator;
    private final StrategyPeriodCandidatePolicy periodPolicy;

    protected AbstractChannelStrategyCandidateCalculator(
            InventoryMovementCandidateFactory movementFactory,
            StrategyCandidateIdGenerator idGenerator,
            StrategyPeriodCandidatePolicy periodPolicy
    ) {
        this.movementFactory = movementFactory;
        this.idGenerator = idGenerator;
        this.periodPolicy = periodPolicy;
    }

    protected abstract boolean requiresCurrentlyListedTarget();

    @Override
    public CandidateGenerationResult generate(
            StrategyCalculationContext context,
            int strategyPriority
    ) {
        Price sourceTerms = sourceCommercialTerms(context);
        if (supportedType() == StrategyType.CHANNEL_EXPANSION && sourceTerms == null) {
            return new CandidateGenerationResult(
                    List.of(),
                    List.of(new CandidateExclusion(
                            supportedType(),
                            null,
                            CandidateExclusionReason.SOURCE_PRICE_INCOMPLETE,
                            "Source commercial terms are required for channel expansion"
                    ))
            );
        }

        List<Long> requestedTargets = orderedTargets(context);
        List<Long> eligibleTargets = new ArrayList<>();
        List<CandidateExclusion> exclusions = new ArrayList<>();
        for (Long targetId : requestedTargets) {
            if (Objects.equals(context.sourceSalesPointId(), targetId)) {
                exclusions.add(exclusion(
                        targetId,
                        CandidateExclusionReason.SAME_AS_SOURCE,
                        "Source sales point cannot be a channel movement target"
                ));
                continue;
            }
            SalesPoint target = context.salesPoints().get(targetId);
            if (target == null) {
                exclusions.add(exclusion(
                        targetId,
                        CandidateExclusionReason.CHANNEL_TARGET_NOT_AVAILABLE,
                        "Target sales point is missing from calculation context"
                ));
                continue;
            }
            boolean listed = target.currentlyListed();
            if (requiresCurrentlyListedTarget() && !listed) {
                exclusions.add(exclusion(
                        targetId,
                        CandidateExclusionReason.TARGET_NOT_LISTED,
                        "Channel concentration requires an existing SKU listing"
                ));
                continue;
            }
            if (requiresCurrentlyListedTarget() && !target.hasCompletePrice()) {
                exclusions.add(exclusion(
                        targetId,
                        CandidateExclusionReason.TARGET_PRICE_INCOMPLETE,
                        "Channel concentration requires complete target commercial terms"
                ));
                continue;
            }
            if (!requiresCurrentlyListedTarget() && listed) {
                exclusions.add(exclusion(
                        targetId,
                        CandidateExclusionReason.TARGET_ALREADY_LISTED,
                        "Channel expansion requires a sales point without an existing listing"
                ));
                continue;
            }
            eligibleTargets.add(targetId);
        }
        if (eligibleTargets.isEmpty()) {
            if (exclusions.isEmpty()) {
                exclusions.add(exclusion(
                        null,
                        CandidateExclusionReason.CHANNEL_TARGET_NOT_AVAILABLE,
                        "No eligible channel target exists"
                ));
            }
            return new CandidateGenerationResult(List.of(), exclusions);
        }

        List<StrategyCandidate> candidates = new ArrayList<>();
        for (int targetIndex = 0; targetIndex < eligibleTargets.size(); targetIndex++) {
            Long targetId = eligibleTargets.get(targetIndex);
            for (StrategyPeriodCandidate period : periodPolicy.generate(context, targetId)) {
                StrategyCalculationContext periodContext = withPeriod(context, period);
                for (StrategyType movementType : List.of(
                        StrategyType.REALLOCATION,
                        StrategyType.RT_TRANSFER
                )) {
                    CandidateGenerationResult movementResult = movementFactory.generate(
                            periodContext,
                            movementType,
                            strategyPriority,
                            List.of(targetId),
                            requiresCurrentlyListedTarget()
                    );
                    exclusions.addAll(movementResult.exclusions());
                    for (StrategyCandidate movementCandidate
                            : movementResult.candidates()) {
                        candidates.add(toChannelCandidate(
                                periodContext,
                                movementCandidate,
                                sourceTerms,
                                targetIndex + 1
                        ));
                    }
                }
            }
        }
        if (candidates.isEmpty() && exclusions.isEmpty()) {
            exclusions.add(exclusion(
                    null,
                    CandidateExclusionReason.CHANNEL_TARGET_NOT_AVAILABLE,
                    "No executable channel candidate exists"
            ));
        }
        return new CandidateGenerationResult(
                candidates,
                List.copyOf(new LinkedHashSet<>(exclusions))
        );
    }

    private StrategyCandidate toChannelCandidate(
            StrategyCalculationContext context,
            StrategyCandidate movementCandidate,
            Price sourceTerms,
            int targetPriority
    ) {
        StrategyCandidate.MovementEvidence movementEvidence =
                (StrategyCandidate.MovementEvidence) movementCandidate.evidence();
        StrategyType movementType = movementCandidate.strategyTypes().get(0);
        StrategyCandidate.Action firstMovement = movementCandidate.actions().get(0);
        Long targetId = firstMovement.target().salesPointId();
        SalesPoint target = context.salesPoints().get(targetId);
        Price appliedTerms = requiresCurrentlyListedTarget()
                ? target.price()
                : sourceTerms;
        BigDecimal totalActionQuantity = sum(movementCandidate.actions().stream()
                .map(StrategyCandidate.Action::actionQuantity)
                .toList());

        List<StrategyCandidate.Action> actions = new ArrayList<>(
                movementCandidate.actions()
        );
        actions.add(new StrategyCandidate.Action(
                supportedType(),
                firstMovement.source(),
                firstMovement.target(),
                totalActionQuantity,
                ZERO_MONEY,
                appliedTerms.actualPrice(),
                null,
                List.of()
        ));
        List<StrategyType> strategyTypes = List.of(supportedType(), movementType);
        List<CandidateAssumption> assumptions = new ArrayList<>(
                movementCandidate.assumptions()
        );
        if (!requiresCurrentlyListedTarget()) {
            assumptions.add(CandidateAssumption.TARGET_COMMERCIAL_TERMS_COPIED_FROM_SOURCE);
        }
        assumptions = List.copyOf(new LinkedHashSet<>(assumptions));

        String candidateId = idGenerator.generate(
                strategyTypes,
                context.forecastStartDate(),
                context.forecastEndDate(),
                actions
        );
        return new StrategyCandidate(
                candidateId,
                strategyTypes,
                context.forecastStartDate(),
                context.forecastEndDate(),
                actions,
                assumptions,
                new StrategyCandidate.Preference(
                        movementCandidate.preference().strategyPriority(),
                        targetPriority,
                        movementCandidate.preference().quantityPercentage()
                ),
                new StrategyCandidate.ChannelEvidence(
                        movementEvidence.maxExecutableQty(),
                        forecastTotal(target.dailyForecast(),
                                context.forecastStartDate(),
                                context.forecastEndDate()),
                        target.existingAvailableQty(),
                        movementEvidence.targetAdditionalDemandQty(),
                        movementType,
                        appliedTerms.actualPrice(),
                        appliedTerms.paymentFee(),
                        appliedTerms.logisticsCost()
                )
        );
    }

    private CandidateExclusion exclusion(
            Long targetId,
            CandidateExclusionReason reason,
            String message
    ) {
        return new CandidateExclusion(supportedType(), targetId, reason, message);
    }

    private static Price sourceCommercialTerms(StrategyCalculationContext context) {
        if (context.sourceSalesPointId() == null) {
            return null;
        }
        SalesPoint source = context.salesPoints().get(context.sourceSalesPointId());
        return source == null ? null : source.price();
    }

    private static StrategyCalculationContext withPeriod(
            StrategyCalculationContext context,
            StrategyPeriodCandidate period
    ) {
        return new StrategyCalculationContext(
                context.strategyCaseId(),
                context.sourceSalesPointId(),
                context.calculatedAt(),
                period.startDate(),
                period.endDate(),
                context.sku(),
                context.unitCost(),
                context.requestConstraints(),
                context.evaluationInventory(),
                context.referenceInventory(),
                context.inventoryPolicies(),
                context.salesPoints(),
                context.forecastMetadata(),
                context.transferRoutes(),
                context.transferCostPolicies()
        );
    }

    private static List<Long> orderedTargets(StrategyCalculationContext context) {
        List<Long> requested = context.requestConstraints()
                .orderedCandidateSalesPointIds();
        return requested.isEmpty()
                ? context.salesPoints().keySet().stream().toList()
                : requested;
    }

    private static BigDecimal forecastTotal(
            Map<LocalDate, BigDecimal> forecast,
            LocalDate start,
            LocalDate end
    ) {
        BigDecimal result = BigDecimal.ZERO;
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            BigDecimal daily = forecast.get(date);
            if (daily == null || daily.signum() < 0) {
                throw new StrategyCalculationException(
                        "CALCULATION_FORECAST_INVALID",
                        "Target daily forecast is missing or negative: " + date
                );
            }
            result = result.add(daily);
        }
        return CalculationPrecisionPolicy.quantity(result);
    }

    private static BigDecimal sum(Iterable<BigDecimal> values) {
        BigDecimal result = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            result = result.add(value);
        }
        return CalculationPrecisionPolicy.quantity(result);
    }
}
