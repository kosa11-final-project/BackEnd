package com.stockit.backend.feature.strategy.calculation.candidate.service.impl;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.strategy.calculation.candidate.calculator.StrategyCandidateCalculator;
import com.stockit.backend.feature.strategy.calculation.candidate.domain.CandidateExclusion;
import com.stockit.backend.feature.strategy.calculation.candidate.domain.CandidateExclusionReason;
import com.stockit.backend.feature.strategy.calculation.candidate.domain.CandidateGenerationResult;
import com.stockit.backend.feature.strategy.calculation.candidate.domain.StrategyCandidate;
import com.stockit.backend.feature.strategy.calculation.candidate.service.StrategyCandidateGenerationService;
import com.stockit.backend.feature.strategy.calculation.candidate.policy.StrategyPeriodEligibilityPolicy;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.domain.StrategyType;

@Service
public class StrategyCandidateGenerationServiceImpl
        implements StrategyCandidateGenerationService {

    private static final List<StrategyType> DEFAULT_GENERATION_TYPES = List.of(
            StrategyType.REALLOCATION,
            StrategyType.RT_TRANSFER,
            StrategyType.PRICE_DISCOUNT,
            StrategyType.CHANNEL_EXPANSION,
            StrategyType.CHANNEL_CONCENTRATION
    );

    private final Map<StrategyType, StrategyCandidateCalculator> calculators;
    private final StrategyPeriodEligibilityPolicy periodEligibilityPolicy;

    public StrategyCandidateGenerationServiceImpl(
            List<StrategyCandidateCalculator> calculators,
            StrategyPeriodEligibilityPolicy periodEligibilityPolicy
    ) {
        Map<StrategyType, StrategyCandidateCalculator> registry = new EnumMap<>(
                StrategyType.class
        );
        for (StrategyCandidateCalculator calculator : calculators) {
            StrategyCandidateCalculator duplicate = registry.put(
                    calculator.supportedType(),
                    calculator
            );
            if (duplicate != null) {
                throw new IllegalStateException(
                        "Duplicate strategy candidate calculator: "
                                + calculator.supportedType()
                );
            }
        }
        this.calculators = Map.copyOf(registry);
        this.periodEligibilityPolicy = periodEligibilityPolicy;
    }

    @Override
    public CandidateGenerationResult generate(StrategyCalculationContext context) {
        Map<String, StrategyCandidate> uniqueCandidates = new LinkedHashMap<>();
        List<CandidateExclusion> exclusions = new ArrayList<>();

        List<StrategyType> orderedTypes = requestedOrDefaultTypes(context);
        for (int index = 0; index < orderedTypes.size(); index++) {
            StrategyType strategyType = orderedTypes.get(index);
            if (context.sourceSalesPointId() == null
                    && strategyType != StrategyType.REALLOCATION
                    && strategyType != StrategyType.RT_TRANSFER) {
                exclusions.add(new CandidateExclusion(
                        strategyType,
                        null,
                        CandidateExclusionReason.PUBLIC_UNASSIGNED_STRATEGY_NOT_SUPPORTED,
                        "Public unassigned inventory supports allocation or physical transfer only"
                ));
                continue;
            }
            StrategyCandidateCalculator calculator = calculators.get(strategyType);
            if (calculator == null) {
                continue;
            }
            CandidateGenerationResult result = calculator.generate(context, index + 1);
            for (StrategyCandidate candidate : result.candidates()) {
                if (!hasEligibleAllocatedPeriod(
                        context,
                        strategyType,
                        candidate,
                        exclusions
                )) {
                    continue;
                }
                StrategyCandidate existing = uniqueCandidates.putIfAbsent(
                        candidate.candidateId(),
                        candidate
                );
                if (existing != null) {
                    exclusions.add(new CandidateExclusion(
                            strategyType,
                            candidate.actions().get(0).target().salesPointId(),
                            CandidateExclusionReason.DUPLICATED_CANDIDATE,
                            "Candidate with identical normalized actions already exists"
                    ));
                }
            }
            exclusions.addAll(result.exclusions());
        }
        return new CandidateGenerationResult(
                List.copyOf(uniqueCandidates.values()),
                exclusions
        );
    }

    private boolean hasEligibleAllocatedPeriod(
            StrategyCalculationContext context,
            StrategyType strategyType,
            StrategyCandidate candidate,
            List<CandidateExclusion> exclusions
    ) {
        if (candidate.endDate() == null) {
            return true;
        }
        List<Long> allocatedIds = candidate.actions().stream()
                .flatMap(action -> action.lotAllocations().stream())
                .map(StrategyCandidate.LotAllocation::inventoryBalanceId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (allocatedIds.isEmpty()) {
            return true;
        }
        try {
            periodEligibilityPolicy.validateAllocatedPeriod(
                    context,
                    candidate.endDate(),
                    allocatedIds
            );
            return true;
        } catch (AppException exception) {
            if (exception.getErrorCode()
                    != ErrorCode.AI_STRATEGY_SELLABLE_END_EXCEEDED) {
                throw exception;
            }
            Long targetSalesPointId = candidate.actions().stream()
                    .map(action -> action.target().salesPointId())
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            exclusions.add(new CandidateExclusion(
                    strategyType,
                    targetSalesPointId,
                    CandidateExclusionReason.LOT_NOT_SELLABLE_IN_PERIOD,
                    exception.getMessage()
            ));
            return false;
        }
    }

    private List<StrategyType> requestedOrDefaultTypes(
            StrategyCalculationContext context
    ) {
        List<StrategyType> requested = context.requestConstraints().orderedStrategyTypes();
        if (context.sourceSalesPointId() == null && requested.isEmpty()) {
            return List.of(
                    StrategyType.REALLOCATION,
                    StrategyType.RT_TRANSFER
            );
        }
        List<StrategyType> source = requested.isEmpty()
                ? DEFAULT_GENERATION_TYPES
                : requested;
        return source.stream().distinct().toList();
    }
}
