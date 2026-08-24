package com.stockit.backend.feature.strategy.simulation;

import com.stockit.backend.feature.strategy.dto.response.AdjustedAiStrategySimulationResponse;

public interface StrategyAdjustmentSimulationService {
    AdjustedAiStrategySimulationResponse simulate(
            Long strategyCaseId,
            String candidateId,
            AdjustStrategySimulationCommand command
    );
}
