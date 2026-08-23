package com.stockit.backend.feature.strategy.calculation.candidate.service.impl;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.stockit.backend.feature.strategy.calculation.candidate.calculator.StrategyCandidateCalculator;
import com.stockit.backend.feature.strategy.calculation.candidate.domain.CandidateExclusion;
import com.stockit.backend.feature.strategy.calculation.candidate.domain.CandidateExclusionReason;
import com.stockit.backend.feature.strategy.calculation.candidate.domain.CandidateGenerationResult;
import com.stockit.backend.feature.strategy.calculation.candidate.domain.StrategyCandidate;
import com.stockit.backend.feature.strategy.calculation.candidate.service.StrategyCandidateGenerationService;
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

    public StrategyCandidateGenerationServiceImpl(
            List<StrategyCandidateCalculator> calculators
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
    }

    @Override
    public CandidateGenerationResult generate(StrategyCalculationContext context) {
        Map<String, StrategyCandidate> uniqueCandidates = new LinkedHashMap<>();
        List<CandidateExclusion> exclusions = new ArrayList<>();

        List<StrategyType> orderedTypes = requestedOrDefaultTypes(context);
        for (int index = 0; index < orderedTypes.size(); index++) {
            StrategyType strategyType = orderedTypes.get(index);
            StrategyCandidateCalculator calculator = calculators.get(strategyType);
            if (calculator == null) {
                continue;
            }
            CandidateGenerationResult result = calculator.generate(context, index + 1);
            for (StrategyCandidate candidate : result.candidates()) {
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

    private List<StrategyType> requestedOrDefaultTypes(
            StrategyCalculationContext context
    ) {
        List<StrategyType> requested = context.requestConstraints().orderedStrategyTypes();
        List<StrategyType> source = requested.isEmpty()
                ? DEFAULT_GENERATION_TYPES
                : requested;
        return source.stream().distinct().toList();
    }
}
