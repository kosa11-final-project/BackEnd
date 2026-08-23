package com.stockit.backend.feature.strategy.calculation.candidate.calculator;

import org.springframework.stereotype.Component;

import com.stockit.backend.feature.strategy.calculation.candidate.domain.StrategyCandidateIdGenerator;
import com.stockit.backend.feature.strategy.calculation.candidate.policy.StrategyPeriodCandidatePolicy;
import com.stockit.backend.feature.strategy.domain.StrategyType;

@Component
public class ChannelConcentrationCandidateCalculator
        extends AbstractChannelStrategyCandidateCalculator {

    public ChannelConcentrationCandidateCalculator(
            InventoryMovementCandidateFactory movementFactory,
            StrategyCandidateIdGenerator idGenerator,
            StrategyPeriodCandidatePolicy periodPolicy
    ) {
        super(movementFactory, idGenerator, periodPolicy);
    }

    @Override
    public StrategyType supportedType() {
        return StrategyType.CHANNEL_CONCENTRATION;
    }

    @Override
    protected boolean requiresCurrentlyListedTarget() {
        return true;
    }
}
