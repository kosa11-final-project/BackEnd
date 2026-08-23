package com.stockit.backend.feature.strategy.calculation.candidate.calculator;

import com.stockit.backend.feature.strategy.calculation.candidate.domain.CandidateGenerationResult;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.domain.StrategyType;

public interface StrategyCandidateCalculator {

    StrategyType supportedType();

    CandidateGenerationResult generate(
            StrategyCalculationContext context,
            int strategyPriority
    );
}
