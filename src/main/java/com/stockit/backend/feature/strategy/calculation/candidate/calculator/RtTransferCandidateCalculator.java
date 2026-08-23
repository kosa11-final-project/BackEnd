package com.stockit.backend.feature.strategy.calculation.candidate.calculator;

import org.springframework.stereotype.Component;

import com.stockit.backend.feature.strategy.calculation.candidate.domain.CandidateGenerationResult;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.domain.StrategyType;

@Component
public class RtTransferCandidateCalculator implements StrategyCandidateCalculator {

    private final InventoryMovementCandidateFactory candidateFactory;

    public RtTransferCandidateCalculator(
            InventoryMovementCandidateFactory candidateFactory
    ) {
        this.candidateFactory = candidateFactory;
    }

    @Override
    public StrategyType supportedType() {
        return StrategyType.RT_TRANSFER;
    }

    @Override
    public CandidateGenerationResult generate(
            StrategyCalculationContext context,
            int strategyPriority
    ) {
        return candidateFactory.generate(context, supportedType(), strategyPriority);
    }
}
