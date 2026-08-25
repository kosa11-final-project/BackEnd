package com.stockit.backend.feature.strategy.simulation;

import java.time.LocalDate;

import com.stockit.backend.feature.strategy.dto.response.AdjustedAiStrategySimulationResponse;

public interface StrategyAdjustmentSimulationService {
    AdjustedAiStrategySimulationResponse simulate(
            Long strategyCaseId,
            String candidateId,
            AdjustStrategySimulationCommand command
    );

    ResolvedStrategyAdjustment resolve(
            Long strategyCaseId,
            String candidateId,
            AdjustStrategySimulationCommand command,
            LocalDate businessDate
    );

    ResolvedStrategyAdjustment resolveForSelection(
            Long strategyCaseId,
            String candidateId,
            AdjustStrategySimulationCommand command,
            LocalDate businessDate
    );
}
